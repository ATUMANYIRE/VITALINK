"""Generate the checked-in OpenAPI 3.0.3 specification (JSON is valid YAML 1.2)."""
import json
from pathlib import Path

def ref(name):
    return {"$ref": "#/components/schemas/" + name}

def response(name, description="Success"):
    return {"description": description, "content": {"application/json": {"schema": ref(name)}}}

def operation(tag, output, *, body=None, doctor=False):
    result = {"tags": [tag], "security": [{"bearerAuth": []}], "responses": {
        "200": response(output),
        **{str(code): response("Problem", title) for code, title in [(400, "Invalid input"), (401, "Unauthenticated"), (403, "Forbidden role or scope"), (404, "Absent or out of scope"), (409, "Immutable snapshot or state conflict"), (503, "Temporarily unavailable")]},
    }}
    if body:
        result["requestBody"] = {"required": True, "content": {"application/json": {"schema": ref(body)}}}
    if doctor:
        result["description"] = "DOCTOR or ADMIN role, restricted to the authenticated facility."
    return result

uuid = {"type": "string", "format": "uuid"}
instant = {"type": "string", "format": "date-time"}
string = {"type": "string"}
status = {"type": "string", "enum": ["SERVER_RECEIVED", "ACKNOWLEDGED", "CLOSED"]}
urgency = {"type": "string", "enum": ["URGENT", "REVIEW_REQUIRED", "NO_TRIGGER"]}

def obj(properties, required=None):
    return {"type": "object", "additionalProperties": False, "properties": properties, "required": list(properties) if required is None else required}

schemas = {
    "Login": obj({"username": {"type": "string", "pattern": "^[A-Za-z0-9_.-]{1,64}$"}, "password": {"type": "string", "format": "password", "minLength": 8, "maxLength": 72}}),
    "User": obj({"username": string, "role": {"type": "string", "enum": ["HEALTH_WORKER", "DOCTOR", "ADMIN"]}, "facilityId": string}),
    "Session": obj({"token": string, "expiresAt": instant, "user": ref("User")}),
    "Assessment": obj({
        "id": uuid, "patientId": {"type": "string", "pattern": "^SYN-[A-Za-z0-9-]{1,40}$"}, "measuredAt": instant,
        "systolic": {"type": "integer", "nullable": True, "minimum": 50, "maximum": 300},
        "diastolic": {"type": "integer", "nullable": True, "minimum": 30, "maximum": 200},
        "gestationalWeeks": {"type": "integer", "nullable": True, "minimum": 0, "maximum": 45},
        "symptoms": {"type": "array", "uniqueItems": True, "maxItems": 7, "items": {"type": "string", "enum": ["SEVERE_HEADACHE", "VISUAL_DISTURBANCE", "UPPER_ABDOMINAL_PAIN", "BREATHLESSNESS", "SEIZURE", "HEAVY_BLEEDING", "REDUCED_FETAL_MOVEMENT"]}},
        "source": {"type": "string", "enum": ["MANUAL", "DEVICE"]},
        "ruleVersion": {"type": "string", "enum": ["vitalink-rules-1.0.0"]},
    }, ["id", "patientId", "measuredAt", "symptoms", "source", "ruleVersion"]),
    "Submission": obj({"id": uuid, "facilityId": {"type": "string", "pattern": "^[A-Za-z0-9-]{1,80}$"}, "assessment": ref("Assessment")}),
    "Referral": obj({"id": uuid, "facilityId": string, "assessment": ref("Assessment"), "status": status, "urgency": urgency, "ruleTriggers": {"type": "array", "items": string}, "receivedAt": instant, "acknowledgedAt": {**instant, "nullable": True}, "acknowledgedBy": {**string, "nullable": True}}),
    "History": {"type": "array", "items": obj({"status": status, "at": instant, "actor": string})},
    "Assessments": {"type": "array", "items": ref("Assessment")},
    "Page": obj({"items": {"type": "array", "items": ref("Referral")}, "page": {"type": "integer"}, "size": {"type": "integer"}, "totalElements": {"type": "integer", "format": "int64"}, "totalPages": {"type": "integer"}}),
    "Metrics": obj({key: {"type": "integer", "format": "int64"} for key in ["total", "urgent", "awaitingAcknowledgment", "acknowledged", "closed"]}),
    "Problem": {"type": "object", "properties": {"type": {"type": "string", "format": "uri"}, "title": string, "status": {"type": "integer"}, "detail": string, "code": string, "instance": string}, "required": ["type", "title", "status", "detail", "code", "instance"]},
    "Health": obj({"status": string, "mode": string}),
}
paths = {
    "/auth/login": {"post": operation("Authentication", "Session", body="Login")},
    "/auth/logout": {"post": {"tags": ["Authentication"], "security": [{"bearerAuth": []}], "responses": {"200": {"description": "Session revoked"}, "401": response("Problem")}}},
    "/sync/referrals": {"post": operation("Referrals", "Referral", body="Submission")},
    "/referrals": {"get": operation("Referrals", "Page")},
    "/referrals/{id}": {"get": operation("Referrals", "Referral")},
    "/referrals/{id}/history": {"get": operation("Referrals", "History")},
    "/referrals/{id}/acknowledge": {"post": operation("Referrals", "Referral", doctor=True)},
    "/referrals/{id}/close": {"post": operation("Referrals", "Referral", doctor=True)},
    "/patients/{patientId}/assessments": {"get": operation("Assessments", "Assessments")},
    "/metrics": {"get": operation("Operations", "Metrics")},
    "/health": {"get": {"tags": ["Operations"], "security": [], "responses": {"200": response("Health")}}},
}
paths["/auth/login"]["post"]["security"] = []
paths["/auth/login"]["post"]["responses"]["429"] = response("Problem", "Login throttled")
paths["/sync/referrals"]["post"].update({"description": "HEALTH_WORKER or ADMIN, own facility. First receipt 201; identical replay 200; immutable conflict 409. Never automatically acknowledges.", "parameters": [{"name": "Idempotency-Key", "in": "header", "required": True, "schema": uuid}]})
paths["/sync/referrals"]["post"]["responses"]["201"] = response("Referral", "First durable receipt")
for path, methods in paths.items():
    for method in methods.values():
        if "{id}" in path:
            method["parameters"] = [{"name": "id", "in": "path", "required": True, "schema": uuid}]
        if "{patientId}" in path:
            method["parameters"] = [{"name": "patientId", "in": "path", "required": True, "schema": schemas["Assessment"]["properties"]["patientId"]}]
paths["/referrals"]["get"]["parameters"] = [
    {"name": "page", "in": "query", "schema": {"type": "integer", "minimum": 0, "maximum": 1000000, "default": 0}},
    {"name": "size", "in": "query", "schema": {"type": "integer", "minimum": 1, "maximum": 100, "default": 20}},
    {"name": "urgency", "in": "query", "schema": urgency},
    {"name": "status", "in": "query", "schema": status},
    {"name": "patientId", "in": "query", "schema": schemas["Assessment"]["properties"]["patientId"]},
]
spec = {"openapi": "3.0.3", "info": {"title": "VITALINK Research Referral API", "version": "1.0.0", "description": "Synthetic records only. Research prototype, not a validated medical device. Deterministic safety rules take priority. No diagnostic or treatment recommendations."}, "servers": [{"url": "/api/v1"}], "security": [{"bearerAuth": []}], "paths": paths, "components": {"securitySchemes": {"bearerAuth": {"type": "http", "scheme": "bearer", "bearerFormat": "opaque token"}}, "schemas": schemas}}
destination = Path(__file__).resolve().parent.parent / "src/main/resources/openapi.yaml"
destination.write_text(json.dumps(spec, indent=2) + "\n", encoding="utf-8")
print("Updated OpenAPI 3.0.3 specification.")
