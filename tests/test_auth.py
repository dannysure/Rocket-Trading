import time

import jwt
import pytest
from fastapi import HTTPException

from api.auth import PUBLISHED_DEVELOPMENT_SECRET, require_internal_role

SECRET = "a-test-secret-that-is-at-least-32-bytes-long"


def token(role, secret=SECRET, issuer="rocket-trading", expires_in=300):
    claims = {"role": role, "clientId": 7, "iss": issuer, "exp": int(time.time()) + expires_in}
    return "Bearer " + jwt.encode(claims, secret.encode(), algorithm="HS256")


@pytest.fixture(autouse=True)
def configured_secret(monkeypatch):
    monkeypatch.setenv("JWT_SECRET", SECRET)
    monkeypatch.setenv("JWT_ISSUER", "rocket-trading")


@pytest.mark.parametrize("role", ["ANALYST", "OPERATIONS"])
def test_internal_roles_are_allowed(role):
    assert require_internal_role(token(role))["role"] == role


@pytest.mark.parametrize("authorization, status", [
    (None, 401),
    ("Basic abc", 401),
    (token("CLIENT"), 403),
    (token("ANALYST", secret="some-other-secret-that-is-32-bytes-long!"), 401),
    (token("ANALYST", issuer="someone-else"), 401),
    (token("ANALYST", expires_in=-60), 401),
])
def test_clients_and_bad_tokens_are_refused(authorization, status):
    with pytest.raises(HTTPException) as refused:
        require_internal_role(authorization)
    assert refused.value.status_code == status


@pytest.mark.parametrize("secret", ["", PUBLISHED_DEVELOPMENT_SECRET])
def test_stays_closed_without_a_real_secret(monkeypatch, secret):
    monkeypatch.setenv("JWT_SECRET", secret)
    with pytest.raises(HTTPException) as refused:
        require_internal_role(token("ANALYST", secret=secret or SECRET))
    assert refused.value.status_code == 503
