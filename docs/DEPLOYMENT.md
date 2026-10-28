# Local deployment and configuration

This release is a local synthetic-data research demonstration. No cloud infrastructure, public endpoint, remote push or competition submission was performed. Clinical deployment is not authorized.

## Windows local services

Use the component READMEs and project scripts rather than changing unrelated services. Required development host ports: PostgreSQL55432, API8087, web3017. Ports8080,3000,3007 were occupied by unrelated applications during the audit.

API workflow: generate runtime demo configuration, start the project-local PostgreSQL cluster, load environment.ps1, run Maven with JDK21, then start-api.ps1/run-local.ps1. The supported foreground PostgreSQL launcher exists because pg_ctl could not create a restricted Windows token in this sandbox. It runs with existing permissions; no security control is disabled. Logs and random credentials stay in services/api/.runtime. Do not print or publish that directory. Stop only project-specific process IDs recorded by these scripts.

API environment: DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD, VITALINK_BOOTSTRAP_USERS (development-only JSON account list), API_BIND_ADDRESS and API_PORT=8087. Passwords are hashed; sessions are random, hashed at rest, time-limited and revocable. Empty bootstrap list creates no default production account. Separate database owner/migration/runtime roles and managed identity are future deployment requirements.

Web environment: API_BASE_URL=http://127.0.0.1:8087/api/v1, APP_ORIGIN=http://localhost:3017, PORT=3017, and a base64-encoded random32-byte SESSION_SECRET. COOKIE_SECURE=false is an explicit localhost HTTP demo exception. Production requests require configured session encryption and secure cookies; use HTTPS and an approved exact origin. Never store bearer tokens in localStorage or publish a session key. The custom in-process Next development server is a supported local fallback where supervisor subprocess spawning fails; it is not production-build evidence.

## Android networking

The emulator reaches host loopback through10.0.2.2; use http://10.0.2.2:8087/api/v1 in a debug build. For a physical device, use adb reverse tcp:8087 tcp:8087 and a debug endpoint http://127.0.0.1:8087/api/v1, or a trusted local network endpoint. Loopback-only API binding deliberately prevents LAN access. Do not expose it broadly just to avoid proper development networking. Release disables cleartext and needs a real reviewed HTTPS endpoint; supplied localhost is not deployed.

## Docker Compose

`python scripts/create_demo_env.py` generates random ignored .env/.runtime files without overwriting an existing .env. `docker compose config --quiet` validates interpolation without printing secrets. `docker compose up --build -d` builds API/web and starts PostgreSQL. Host mappings are127.0.0.1:8087 and127.0.0.1:3017; the database is internal. Docker engine access was initially denied, so consult actual final evidence before claiming startup.

The Compose database role/bootstrap configuration is for local demonstration. Before any deployment: independently review dependencies/security advisories, TLS, cookie/CSRF/origin policy, least-privilege roles, backup restoration, audit retention, key lifecycle, monitoring, rate limiting across instances, clinical escalation and all governance requirements. No default production credentials are supplied.
