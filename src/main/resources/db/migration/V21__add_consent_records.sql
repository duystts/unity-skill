-- Story 9.1: Onboarding consent flow — record per-user explicit consent.
-- consent_version allows future consent re-prompting if terms change.
CREATE TABLE consent_records (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID        NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    consented_at     TIMESTAMPTZ NOT NULL,
    consent_version  VARCHAR(20) NOT NULL DEFAULT '1.0',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_consent_records_user_id ON consent_records(user_id);
