"""Create random Compose-only demo credentials; never overwrite an existing .env."""

import json
import base64
import secrets
from pathlib import Path

root = Path(__file__).resolve().parents[1]
destination = root / ".env"
if destination.exists():
    raise SystemExit(".env already exists; preserved. Use existing demo configuration.")
users = [{"username": name, "password": secrets.token_urlsafe(24), "role": role, "facilityId": facility}
         for name, role, facility in (("worker", "HEALTH_WORKER", "demo-facility"),
                                      ("doctor", "DOCTOR", "demo-facility"),
                                      ("admin", "ADMIN", "demo-facility"),
                                      ("outsider", "DOCTOR", "other-facility"),
                                      ("outsider-admin", "ADMIN", "other-facility"))]
destination.write_text("POSTGRES_DB=vitalink\nPOSTGRES_USER=vitalink_demo\nPOSTGRES_PASSWORD=" + secrets.token_urlsafe(32)
                       + "\nSESSION_SECRET=" + base64.b64encode(secrets.token_bytes(32)).decode("ascii")
                       + "\nVITALINK_BOOTSTRAP_USERS='" + json.dumps(users, separators=(",", ":")) + "'\n", encoding="utf-8")
private = root / ".runtime"
private.mkdir(exist_ok=True)
(private / "compose-demo-users.json").write_text(json.dumps({"users": users}, indent=2), encoding="utf-8")
print("Created ignored .env and .runtime/compose-demo-users.json with random credentials. Do not publish either file.")
