-- V12: Add contribution_events table for AI contribution intelligence (Story 6.1)
CREATE TABLE contribution_events (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id   UUID         NOT NULL REFERENCES workspaces(id)  ON DELETE CASCADE,
    user_id        UUID         NOT NULL REFERENCES users(id)        ON DELETE CASCADE,
    source_type    VARCHAR(20)  NOT NULL,
    source_ref_id  UUID,
    skill_signals  TEXT,
    processed_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_contribution_events_workspace_user ON contribution_events(workspace_id, user_id);
CREATE INDEX idx_contribution_events_user ON contribution_events(user_id);
