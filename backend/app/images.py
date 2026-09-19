"""Image upload validation and database-backed image storage.

Uploaded images are validated by decoding their actual bytes (Pillow), never by
filename extension or a client-supplied Content-Type, then stored as binary in
the ``image_assets`` table. Responses reference them through the immutable
serving route ``/api/v1/images/{asset_id}``.
"""

import hashlib
import io
import os
import re
from urllib.parse import urlparse

from fastapi import HTTPException
from PIL import Image
from sqlalchemy import func, select

from .models import AppIcon, Business, ImageAsset
from .services import fail

UPLOAD_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "uploads"
)

MAX_IMAGE_BYTES = 2 * 1024 * 1024
# Decoded-pixel ceiling guards against decompression bombs inside a 2 MB file.
MAX_IMAGE_PIXELS = 25_000_000
READ_CHUNK = 64 * 1024

IMAGES_ROUTE = "/api/v1/images/"

# Raster formats only. SVG is deliberately excluded: it is scriptable markup,
# not a raster image, and serving it from the API origin is unsafe.
FORMAT_CONTENT_TYPES = {
    "PNG": "image/png",
    "JPEG": "image/jpeg",
    "WEBP": "image/webp",
    "ICO": "image/x-icon",
}
SUPPORTED_MESSAGE = "Upload a PNG, JPEG, WebP or ICO image"

_ASSET_ID_RE = re.compile(
    re.escape(IMAGES_ROUTE) + r"([0-9a-fA-F]{8}-[0-9a-fA-F-]{27})\s*$"
)


def asset_url(asset_id: str) -> str:
    """Relative serving URL for an asset; safe for any client base URL."""
    return f"{IMAGES_ROUTE}{asset_id}"


def match_asset_id(url) -> str | None:
    """Return the asset id when the value is one of our relative serving URLs."""
    if not isinstance(url, str):
        return None
    match = _ASSET_ID_RE.search(url.strip())
    return match.group(1).lower() if match else None


def clean_filename(name: str | None) -> str:
    """Original filename for display/metadata only; never used as a path."""
    base = os.path.basename((name or "").replace("\\", "/")).strip()
    return (base or "icon")[:255]


async def read_bounded(upload, limit: int = MAX_IMAGE_BYTES) -> bytes:
    """Read a multipart upload in chunks, aborting past the decoded-size limit."""
    buffer = bytearray()
    while True:
        chunk = await upload.read(READ_CHUNK)
        if not chunk:
            break
        buffer += chunk
        if len(buffer) > limit:
            fail("Image size exceeds 2MB limit", 400)
    return bytes(buffer)


def validate_image(raw: bytes) -> str:
    """Validate actual image content and return the canonical MIME type."""
    if not raw:
        fail("Empty image upload", 400)
    if len(raw) > MAX_IMAGE_BYTES:
        fail("Image size exceeds 2MB limit", 400)
    head = raw[:256].lstrip().lower()
    if head.startswith(b"<?xml") or head.startswith(b"<svg"):
        fail("SVG icons are not supported; " + SUPPORTED_MESSAGE, 400)
    try:
        with Image.open(io.BytesIO(raw)) as image:
            fmt = image.format
            if fmt not in FORMAT_CONTENT_TYPES:
                fail(f"Unsupported image format {fmt or 'UNKNOWN'}; {SUPPORTED_MESSAGE}", 400)
            if image.width * image.height > MAX_IMAGE_PIXELS:
                fail("Image dimensions are too large", 400)
            image.load()  # decode fully: truncated/corrupt files raise here
    except HTTPException:
        raise
    except Exception:
        fail("File content is not a valid image; " + SUPPORTED_MESSAGE, 400)
    return FORMAT_CONTENT_TYPES[fmt]


async def store_image(db, raw: bytes, filename: str | None) -> ImageAsset:
    """Persist validated bytes and return the flushed asset row."""
    content_type = validate_image(raw)
    asset = ImageAsset(
        data=raw,
        content_type=content_type,
        filename=clean_filename(filename),
        size=len(raw),
        sha256=hashlib.sha256(raw).hexdigest(),
    )
    db.add(asset)
    await db.flush()
    return asset


def upload_response(asset: ImageAsset) -> dict:
    return {
        "id": asset.id,
        "filename": asset.filename,
        "image_url": asset_url(asset.id),
        "content_type": asset.content_type,
        "size": asset.size,
    }


async def release_orphaned_asset(db, asset_id: str | None) -> None:
    """Delete an asset only when no icon or business references it anymore.

    Assets are shared safely: an uploaded image can back several icons, and an
    upload that was never assigned is retained until a cleanup policy removes
    it. This helper is the only deletion path and it never removes a row that
    is still referenced.
    """
    if not asset_id:
        return
    result = await db.execute(
        select(func.count(AppIcon.id)).where(AppIcon.asset_id == asset_id)
    )
    if result.scalar():
        return
    result = await db.execute(
        select(func.count(Business.id)).where(Business.icon_asset_id == asset_id)
    )
    if result.scalar():
        return
    asset = await db.get(ImageAsset, asset_id)
    if asset is not None:
        await db.delete(asset)


# ---------------------------------------------------------------------------
# Legacy migration helpers (see app.manage migrate-images / upgrade-db).
# ---------------------------------------------------------------------------


def ensure_image_asset_schema_on_connection(conn) -> None:
    """Idempotently add the image-asset table and FK columns to a live database."""
    from sqlalchemy import inspect, text

    dialect = conn.dialect.name
    inspector = inspect(conn)
    ImageAsset.__table__.create(conn, checkfirst=True)
    AppIcon.__table__.create(conn, checkfirst=True)

    def add_column(table: str, column: str, definition: str) -> None:
        columns = {c["name"] for c in inspect(conn).get_columns(table)}
        if column in columns:
            return
        clause = "IF NOT EXISTS " if dialect == "postgresql" else ""
        conn.execute(
            text(f"ALTER TABLE {table} ADD COLUMN {clause}{column} {definition}")
        )

    add_column("app_icons", "asset_id", "VARCHAR(36) REFERENCES image_assets(id)")
    add_column(
        "businesses", "icon_asset_id", "VARCHAR(36) REFERENCES image_assets(id)"
    )


def local_upload_relative_path(url: str | None) -> str | None | bool:
    """Map a stored legacy URL to a safe path relative to the uploads root.

    Returns the relative path for local upload references, ``None`` for values
    that are not upload references (external URLs, already-migrated serving
    URLs, blanks), or ``False`` for anything attempting to escape the uploads
    directory (path traversal). Handles both ``/uploads/…`` relative paths and
    legacy absolute URLs (``http://host:port/uploads/…``) without fetching them.
    """
    if not url:
        return None
    value = url.strip()
    if match_asset_id(value):
        return None  # already points at a database-backed image
    path = None
    if value.startswith("/uploads/"):
        path = value[len("/uploads/") :]
    elif value.startswith("uploads/"):
        path = value[len("uploads/") :]
    elif value.startswith(("http://", "https://")):
        parsed = urlparse(value)
        if parsed.path.startswith("/uploads/"):
            path = parsed.path[len("/uploads/") :]
        else:
            return None  # genuine external reference, preserved as legacy
    else:
        return None
    segments = [segment for segment in path.split("/") if segment not in ("", ".")]
    if (
        not segments
        or any(segment == ".." or "\\" in segment for segment in segments)
        or path.startswith("/")
    ):
        return False
    return "/".join(segments)
