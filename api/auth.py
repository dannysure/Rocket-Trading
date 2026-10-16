"""
Sign-in checks for the analytics API (BRD 9.3: internal access limited to what a role needs).

Analytics accepts the same bearer tokens as the trading API, signed with the shared JWT_SECRET,
and only for the internal ANALYST and OPERATIONS roles. Without JWT_SECRET it stays closed.
"""

import base64
import binascii
import os

import jwt
from fastapi import Header, HTTPException

INTERNAL_ROLES = {"ANALYST", "OPERATIONS"}
# The value once published in .env.example; the trading API refuses to sign with it, so analytics does too
PUBLISHED_DEVELOPMENT_SECRET = "change-me-development-jwt-secret-1234567890"


def signing_key(secret: str) -> bytes:
    """The HMAC key the trading API derives from JWT_SECRET (raw if 32+ bytes, else base64)."""
    raw = secret.encode("utf-8")
    if len(raw) >= 32:
        return raw
    try:
        decoded = base64.b64decode(secret, validate=True)
    except (binascii.Error, ValueError) as error:
        raise ValueError("JWT secret must be at least 32 bytes or valid base64") from error
    if len(decoded) < 32:
        raise ValueError("JWT secret must be at least 32 bytes")
    return decoded


def require_internal_role(authorization: str | None = Header(default=None)) -> dict:
    """FastAPI dependency: the caller's token claims, if they are signed in as analyst or operations."""
    secret = os.getenv("JWT_SECRET", "")
    if not secret or secret == PUBLISHED_DEVELOPMENT_SECRET:
        raise HTTPException(status_code=503, detail="Analytics sign-in is not configured (set JWT_SECRET)")
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Sign in to view analytics")
    try:
        claims = jwt.decode(
            authorization[len("Bearer "):],
            signing_key(secret),
            algorithms=["HS256", "HS384", "HS512"],
            issuer=os.getenv("JWT_ISSUER", "rocket-trading"),
            options={"require": ["exp", "iss"]},
        )
    except (jwt.PyJWTError, ValueError):
        raise HTTPException(status_code=401, detail="Sign-in token is invalid or has expired")
    if claims.get("role") not in INTERNAL_ROLES:
        raise HTTPException(status_code=403, detail="Your role does not have access to analytics")
    return claims
