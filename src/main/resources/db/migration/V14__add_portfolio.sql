-- Story 7.2: Add publish/unpublish support to skill_evidences
ALTER TABLE skill_evidences
    ADD COLUMN is_published BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN published_at TIMESTAMPTZ;

CREATE INDEX idx_skill_evidences_published ON skill_evidences(user_id, is_published);
