import os
from datetime import datetime, timedelta, timezone

import jwt
from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pwdlib import PasswordHash

from .db import get_db
from .models import User

SECRET = os.getenv("JWT_SECRET", "")
MODE = os.getenv("APP_ENV", "development")
if len(SECRET) < 32:
    raise RuntimeError(
        "Set JWT_SECRET to a random secret of at least 32 characters (see README)."
    )
MOCK = os.getenv("ENABLE_MOCK_PAYMENTS", "false").lower() == "true"
if MODE != "development" and MOCK:
    raise RuntimeError("Mock payments cannot be enabled outside development.")
passwords = PasswordHash.recommended()
bearer = HTTPBearer()
DUMMY_HASH = passwords.hash("dummy-not-a-login-password")


def token(user):
    return jwt.encode(
        {
            "sub": user.id,
            "ver": user.token_version,
            "exp": datetime.now(timezone.utc) + timedelta(hours=1),
            "iss": "ahsan-traders",
            "aud": "ahsan-apps",
        },
        SECRET,
        algorithm="HS256",
    )


def current_user(
    auth: HTTPAuthorizationCredentials = Depends(bearer),
    db=Depends(get_db, scope="function"),
):
    try:
        claims = jwt.decode(
            auth.credentials,
            SECRET,
            algorithms=["HS256"],
            audience="ahsan-apps",
            issuer="ahsan-traders",
            options={"require": ["exp", "sub", "ver"]},
        )
        user = db.get(User, claims["sub"])
        if not user or user.token_version != claims["ver"]:
            raise ValueError()
        return user
    except (jwt.PyJWTError, ValueError, KeyError):
        raise HTTPException(401, "Invalid or expired token")


def admin(user=Depends(current_user)):
    if user.role not in ("ADMIN", "SUPERADMIN"):
        raise HTTPException(403, "Admin access required")
    return user


def root(user=Depends(admin)):
    if user.role != "SUPERADMIN":
        raise HTTPException(403, "Owner access required")
    return user


def investor(user=Depends(current_user)):
    if user.role != "INVESTOR":
        raise HTTPException(403, "Investor account required")
    return user


def verified(user=Depends(investor)):
    if user.kyc_status != "VERIFIED" and not (
        MODE == "development" and MOCK and user.kyc_status == "MOCK_VERIFIED"
    ):
        raise HTTPException(
            403,
            "Verified KYC required; development users can use the explicit mock verification endpoint",
        )
    return user
