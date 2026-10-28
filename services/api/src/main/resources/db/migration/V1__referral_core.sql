CREATE TABLE facility (
    id VARCHAR(80) PRIMARY KEY
);
CREATE TABLE app_user (
    username VARCHAR(64) PRIMARY KEY,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('HEALTH_WORKER','DOCTOR','ADMIN')),
    facility_id VARCHAR(80) NOT NULL REFERENCES facility(id),
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE auth_session (
    token_hash CHAR(64) PRIMARY KEY,
    username VARCHAR(64) NOT NULL REFERENCES app_user(username),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX auth_session_expiry_idx ON auth_session(expires_at);
CREATE TABLE patient (
    id VARCHAR(44) NOT NULL CHECK (id ~ '^SYN-[A-Za-z0-9-]{1,40}$'),
    facility_id VARCHAR(80) NOT NULL REFERENCES facility(id),
    PRIMARY KEY (id, facility_id)
);
CREATE TABLE assessment (
    id UUID PRIMARY KEY,
    patient_id VARCHAR(44) NOT NULL,
    facility_id VARCHAR(80) NOT NULL,
    measured_at TIMESTAMPTZ NOT NULL,
    snapshot JSONB NOT NULL,
    FOREIGN KEY(patient_id, facility_id) REFERENCES patient(id,facility_id)
);
CREATE INDEX assessment_patient_idx ON assessment(facility_id,patient_id,measured_at);
CREATE TABLE referral (
    id UUID PRIMARY KEY,
    facility_id VARCHAR(80) NOT NULL REFERENCES facility(id),
    assessment_id UUID NOT NULL REFERENCES assessment(id),
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('SERVER_RECEIVED','ACKNOWLEDGED','CLOSED')),
    urgency VARCHAR(20) NOT NULL CHECK (urgency IN ('URGENT','REVIEW_REQUIRED','NO_TRIGGER')),
    rule_triggers JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMPTZ,
    acknowledged_by VARCHAR(64) REFERENCES app_user(username),
    CHECK ((status = 'SERVER_RECEIVED' AND acknowledged_at IS NULL AND acknowledged_by IS NULL)
       OR (status IN ('ACKNOWLEDGED','CLOSED') AND acknowledged_at IS NOT NULL AND acknowledged_by IS NOT NULL))
);
CREATE INDEX referral_queue_idx ON referral(facility_id,urgency,received_at,id);
CREATE TABLE referral_history (
    sequence BIGSERIAL PRIMARY KEY,
    referral_id UUID NOT NULL REFERENCES referral(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor VARCHAR(64) NOT NULL REFERENCES app_user(username)
);
CREATE INDEX referral_history_idx ON referral_history(referral_id,sequence);
CREATE TABLE audit_event (
    id BIGSERIAL PRIMARY KEY,
    at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor VARCHAR(64),
    action VARCHAR(40) NOT NULL,
    resource_id UUID,
    facility_id VARCHAR(80)
);
-- Protect immutable clinical snapshots even if an application update accidentally attempts mutation.
CREATE FUNCTION reject_assessment_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Assessment snapshots are immutable';
END;
$$;
CREATE TRIGGER assessment_immutable BEFORE UPDATE ON assessment
    FOR EACH ROW EXECUTE FUNCTION reject_assessment_mutation();
