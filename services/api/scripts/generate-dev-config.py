"""Generate local-only synthetic demo accounts. Never prints secrets."""
import json
import secrets
from pathlib import Path

runtime = Path(__file__).resolve().parent.parent / ".runtime"
runtime.mkdir(parents=True, exist_ok=True)
config_path = runtime / "demo-credentials.json"
if not config_path.exists():
    config = {
        "database": {"username": "vitalink", "password": secrets.token_urlsafe(32), "name": "vitalink", "port": 55432},
        "users": [
            {"username": "worker", "password": secrets.token_urlsafe(24), "role": "HEALTH_WORKER", "facilityId": "demo-facility"},
            {"username": "doctor", "password": secrets.token_urlsafe(24), "role": "DOCTOR", "facilityId": "demo-facility"},
            {"username": "admin", "password": secrets.token_urlsafe(24), "role": "ADMIN", "facilityId": "demo-facility"},
            {"username": "outsider", "password": secrets.token_urlsafe(24), "role": "DOCTOR", "facilityId": "other-facility"},
            {"username": "outsider-admin", "password": secrets.token_urlsafe(24), "role": "ADMIN", "facilityId": "other-facility"},
        ],
    }
    config_path.write_text(json.dumps(config, indent=2), encoding="utf-8")
    (runtime / "db-password.txt").write_text(config["database"]["password"], encoding="utf-8")
print("Local runtime configuration available in services/api/.runtime/demo-credentials.json (ignored by Git).")
