-- V10: Add meetings table for meeting scheduling (Story 5.2)
CREATE TABLE meetings (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    project_id    UUID         NOT NULL REFERENCES projects(id)   ON DELETE CASCADE,
    title         VARCHAR(255) NOT NULL,
    scheduled_at  TIMESTAMPTZ  NOT NULL,
    status        VARCHAR(50)  NOT NULL DEFAULT 'SCHEDULED',
    agenda        TEXT,
    agenda_status VARCHAR(50),
    summary       TEXT,
    action_items  TEXT,
    created_by    UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_meetings_workspace_scheduled
    ON meetings(workspace_id, scheduled_at ASC);
