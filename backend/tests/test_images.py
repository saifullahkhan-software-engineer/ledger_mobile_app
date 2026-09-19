"""Database-backed image uploads, serving, assignment and lifecycle tests."""
import base64
import io
import os

import pytest
from PIL import Image
from sqlalchemy import event, select

from app import db as database
from app.db import Session
from app.images import UPLOAD_DIR
from app.models import AppIcon, Business, ImageAsset

P = "/api/v1"


def encode(fmt="PNG", w=4, h=4, color=(200, 30, 30)):
    buf = io.BytesIO()
    Image.new("RGB", (w, h), color).save(buf, format=fmt)
    return buf.getvalue()


PNG = encode()
PNG_B = encode(color=(30, 30, 200))
JPEG = encode("JPEG")
WEBP = encode("WEBP")
SVG = b'<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>'


def upload(c, h, raw=PNG, name="icon.png", mime="image/png", status=200):
    r = c.post(P + "/admin/icons/upload", files={"file": (name, raw, mime)}, headers=h)
    assert r.status_code == status, r.text
    return r.json() if status == 200 else r


def b64(c, h, raw=PNG, name="icon.png", prefix=False, status=200):
    data = base64.b64encode(raw).decode()
    if prefix:
        data = "data:image/png;base64," + data
    r = c.post(
        P + "/admin/icons/upload-base64",
        json={"filename": name, "data": data},
        headers=h,
    )
    assert r.status_code == status, r.text
    return r.json() if status == 200 else r


def set_icon(c, h, key, url, fallback="store", status=200):
    body = {
        "label": key.replace("_", " ").title(),
        "screen": "dashboard",
        "image_url": url,
    }
    if url:  # the reset flow omits the fallback, as the admin app does
        body["fallback_icon"] = fallback
    r = c.put(f"{P}/admin/icons/{key}", json=body, headers=h)
    assert r.status_code == status, r.text
    return r.json() if status == 200 else r


def asset_ids():
    with Session() as db:
        return set(db.execute(select(ImageAsset.id)).scalars())


def uploads_snapshot():
    path = os.path.join(UPLOAD_DIR, "icons")
    return set(os.listdir(path)) if os.path.isdir(path) else set()


def test_multipart_upload_and_exact_byte_serving(client, admin_headers):
    result = upload(client, admin_headers)
    assert set(result) == {"id", "filename", "image_url", "content_type", "size"}
    assert result["image_url"] == f"/api/v1/images/{result['id']}"
    assert result["image_url"].startswith("/")  # relative, not a baked-in host
    assert result["content_type"] == "image/png"
    assert result["size"] == len(PNG)

    r = client.get(result["image_url"])
    assert r.status_code == 200, r.text
    assert r.content == PNG  # exact bytes round-tripped through the database
    assert r.headers["content-type"] == "image/png"
    assert int(r.headers["content-length"]) == len(PNG)
    assert "immutable" in r.headers["cache-control"]
    assert r.headers["x-content-type-options"] == "nosniff"
    assert r.headers["etag"]
    not_modified = client.get(
        result["image_url"], headers={"If-None-Match": r.headers["etag"]}
    )
    assert not_modified.status_code == 304 and not_modified.content == b""


@pytest.mark.parametrize("fmt,raw,ctype", [
    ("PNG", PNG, "image/png"),
    ("JPEG", JPEG, "image/jpeg"),
    ("WEBP", WEBP, "image/webp"),
])
def test_each_raster_format_detected_from_content(client, admin_headers, fmt, raw, ctype):
    # A wrong file extension and a wrong client MIME are ignored: real bytes win.
    result = upload(client, admin_headers, raw, name=f"icon-{fmt.lower()}.txt", mime="text/plain")
    assert result["content_type"] == ctype
    assert client.get(result["image_url"]).content == raw


def test_base64_upload_variants_and_bounds(client, admin_headers):
    result = b64(client, admin_headers, prefix=True)
    assert client.get(result["image_url"]).content == PNG
    b64(client, admin_headers, b"!!!not-base64!!!", status=400)
    oversized = base64.b64encode(b"\x00" * (2 * 1024 * 1024 + 1)).decode()
    r = client.post(
        P + "/admin/icons/upload-base64",
        json={"filename": "big.png", "data": oversized},
        headers=admin_headers,
    )
    assert r.status_code == 400 and "2MB" in r.json()["detail"]
    huge = base64.b64encode(b"\x00" * (3 * 1024 * 1024)).decode()
    r = client.post(
        P + "/admin/icons/upload-base64",
        json={"filename": "huge.png", "data": huge},
        headers=admin_headers,
    )
    assert r.status_code == 422  # bounded by request schema before decode


def test_malformed_and_unsafe_uploads_rejected(client, admin_headers):
    upload(client, admin_headers, b"this is not an image", name="fake.png", status=400)
    upload(client, admin_headers, PNG[:20], name="truncated.png", status=400)
    upload(client, admin_headers, SVG, name="icon.svg", mime="image/svg+xml", status=400)
    upload(client, admin_headers, encode("GIF"), name="anim.gif", mime="image/gif", status=400)
    upload(client, admin_headers, b"", name="empty.png", status=400)
    upload(client, admin_headers, b"\x00" * (2 * 1024 * 1024 + 1), name="big.png", status=400)
    assert asset_ids() == set()


def test_upload_and_icon_change_authorization(client, investor_headers):
    manager = client.post(
        P + "/auth/login",
        json={"phone": "+923001234568", "password": "AdminTest123!"},
    ).json()
    manager_headers = {"Authorization": "Bearer " + manager["access_token"]}
    for headers, expected in ((None, 401), (investor_headers, 403), (manager_headers, 403)):
        for call in (
            lambda h: client.post(P + "/admin/icons/upload", files={"file": ("i.png", PNG, "image/png")}, headers=h),
            lambda h: client.post(P + "/admin/icons/upload-base64", json={"filename": "i.png", "data": base64.b64encode(PNG).decode()}, headers=h),
            lambda h: client.put(P + "/admin/icons/app_logo", json={"label": "x", "screen": "dashboard", "image_url": "/uploads/x.png"}, headers=h),
            lambda h: client.put(P + "/admin/businesses/chicken/icon", json={"icon_url": "/uploads/x.png"}, headers=h),
        ):
            assert call(headers).status_code == expected
    # Reading published images stays open like the legacy /uploads files.
    assert client.get("/api/v1/images/00000000-0000-0000-0000-000000000000").status_code == 404


def test_missing_and_dangling_assets(client, admin_headers):
    assert client.get("/api/v1/images/images-not-a-uuid").status_code == 404
    result = set_icon(client, admin_headers, "ghost", "/api/v1/images/d8b4bd50-0295-4d05-9d0d-a79e5f5fd4a2", status=400)
    assert "no longer exists" in result.json()["detail"]


def test_assignment_replacement_and_reset(client, admin_headers):
    a = upload(client, admin_headers, PNG)
    b = upload(client, admin_headers, PNG_B)

    saved = set_icon(client, admin_headers, "app_logo", a["image_url"])
    assert saved["asset_id"] == a["id"]
    mobile = client.get(P + "/mobile/icons").json()
    assert mobile["icons"]["app_logo"]["image_url"] == a["image_url"]
    assert a["id"] in asset_ids()

    replaced = set_icon(client, admin_headers, "app_logo", b["image_url"])
    assert replaced["asset_id"] == b["id"]
    assert client.get(b["image_url"]).content == PNG_B
    assert client.get(a["image_url"]).status_code == 404  # old asset released
    assert a["id"] not in asset_ids() and b["id"] in asset_ids()

    removed = set_icon(client, admin_headers, "app_logo", "")
    assert removed["image_url"] == "" and removed["asset_id"] is None
    assert b["id"] not in asset_ids()
    with Session() as db:
        icon = db.execute(select(AppIcon).where(AppIcon.key == "app_logo")).scalar_one()
        assert icon.image_url == "" and icon.fallback_icon is None and icon.asset_id is None


def test_shared_asset_survives_until_last_reference_removed(client, admin_headers):
    a = upload(client, admin_headers, PNG)
    set_icon(client, admin_headers, "app_logo", a["image_url"])
    r = client.put(
        P + "/admin/businesses/chicken/icon",
        json={"icon_url": a["image_url"]},
        headers=admin_headers,
    )
    assert r.status_code == 200, r.text
    with Session() as db:
        assert db.get(Business, "chicken").icon_asset_id == a["id"]

    b = upload(client, admin_headers, PNG_B)
    set_icon(client, admin_headers, "app_logo", b["image_url"])
    assert a["id"] in asset_ids()  # still referenced by the business
    assert client.get(a["image_url"]).status_code == 200

    reset = client.put(
        P + "/admin/businesses/chicken/icon",
        json={"icon_url": ""},
        headers=admin_headers,
    )
    assert reset.status_code == 200 and reset.json()["icon_url"] == ""
    assert a["id"] not in asset_ids()  # orphaned after the last reference
    mobile = client.get(P + "/mobile/icons").json()
    assert "chicken" not in mobile["business_icons"]


def test_external_url_assignment_kept_without_asset(client, admin_headers):
    saved = set_icon(client, admin_headers, "cdn_icon", "https://cdn.example.com/x.png")
    assert saved["asset_id"] is None
    r = client.put(
        P + "/admin/businesses/lpg/icon",
        json={"icon_url": "https://cdn.example.com/lpg.png"},
        headers=admin_headers,
    )
    assert r.status_code == 200 and r.json()["icon_url"] == "https://cdn.example.com/lpg.png"
    with Session() as db:
        assert db.get(Business, "lpg").icon_asset_id is None


def test_list_endpoints_never_load_image_bytes(client, admin_headers):
    first = upload(client, admin_headers, PNG)
    set_icon(client, admin_headers, "app_logo", first["image_url"])
    client.put(
        P + "/admin/businesses/chicken/icon",
        json={"icon_url": first["image_url"]},
        headers=admin_headers,
    )

    statements = []
    def record(conn, cursor, statement, parameters, context, executemany):
        statements.append(statement)

    event.listen(database.engine.sync_engine, "before_cursor_execute", record)
    try:
        assert client.get(P + "/mobile/icons").status_code == 200
        assert client.get(P + "/admin/icons", headers=admin_headers).status_code == 200
        assert client.get(P + "/admin/businesses", headers=admin_headers).status_code == 200
    finally:
        event.remove(database.engine.sync_engine, "before_cursor_execute", record)
    assert statements
    assert not any("image_assets" in s.lower() for s in statements)

    for payload in (
        client.get(P + "/mobile/icons").json(),
        client.get(P + "/admin/icons", headers=admin_headers).json(),
        client.get(P + "/admin/businesses", headers=admin_headers).json(),
    ):
        text = str(payload)
        assert "data:image" not in text and "base64" not in text
        assert "image_assets" not in text and len(text) < 100_000


def test_new_uploads_never_touch_uploads_directory(client, admin_headers):
    before = uploads_snapshot()
    asset = upload(client, admin_headers, PNG, name="from-gallery.png")
    b64(client, admin_headers, PNG_B, name="second.png")
    set_icon(client, admin_headers, "app_logo", asset["image_url"])
    set_icon(client, admin_headers, "app_logo", "")
    assert uploads_snapshot() == before
