#!/usr/bin/env python3
"""Configure this local Keycloak realm to send email through Gmail SMTP."""

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


def load_dotenv(path: Path) -> None:
    if not path.is_file():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip().strip('"').strip("'")
        os.environ.setdefault(key.strip(), value)


def request(url: str, *, data: bytes | None = None, headers: dict[str, str] | None = None, method: str | None = None):
    req = urllib.request.Request(url, data=data, headers=headers or {}, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            return response.read()
    except urllib.error.HTTPError as exc:
        raise RuntimeError(f"Keycloak request failed with HTTP {exc.code}") from None
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Could not reach Keycloak: {exc.reason}") from None


def main() -> int:
    project_root = Path(__file__).resolve().parents[2]
    load_dotenv(project_root / ".env")

    base_url = os.getenv("KEYCLOAK_URL", "http://localhost:8180").rstrip("/")
    realm = os.getenv("KEYCLOAK_REALM", "booknest")
    admin_user = os.getenv("KEYCLOAK_ADMIN_USERNAME", "admin")
    admin_password = os.getenv("KEYCLOAK_ADMIN_PASSWORD", "booknest-local-admin")
    sender = os.getenv("KEYCLOAK_SMTP_FROM", "").strip()
    smtp_user = os.getenv("KEYCLOAK_SMTP_USERNAME", "").strip()
    smtp_password = "".join(os.getenv("KEYCLOAK_SMTP_PASSWORD", "").split())

    if not sender or not smtp_user or not smtp_password:
        print("Set KEYCLOAK_SMTP_FROM, KEYCLOAK_SMTP_USERNAME, and KEYCLOAK_SMTP_PASSWORD in .env.", file=sys.stderr)
        return 2

    token_body = urllib.parse.urlencode({
        "client_id": "admin-cli",
        "grant_type": "password",
        "username": admin_user,
        "password": admin_password,
    }).encode()
    token_response = request(
        f"{base_url}/realms/master/protocol/openid-connect/token",
        data=token_body,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    token = json.loads(token_response)["access_token"]
    api_url = f"{base_url}/admin/realms/{urllib.parse.quote(realm, safe='')}"
    auth_headers = {"Authorization": f"Bearer {token}"}
    realm_config = json.loads(request(api_url, headers=auth_headers))
    realm_config["smtpServer"] = {
        "host": "smtp.gmail.com",
        "port": "587",
        "from": sender,
        "fromDisplayName": "msrbooknest",
        "auth": "true",
        "user": smtp_user,
        "password": smtp_password,
        "ssl": "false",
        "starttls": "true",
    }
    request(
        api_url,
        data=json.dumps(realm_config).encode(),
        headers={**auth_headers, "Content-Type": "application/json"},
        method="PUT",
    )
    print(f"Configured Keycloak Gmail SMTP for realm '{realm}' (smtp.gmail.com:587).")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, KeyError, json.JSONDecodeError) as exc:
        print(str(exc), file=sys.stderr)
        raise SystemExit(1)
