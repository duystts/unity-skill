CREATE TABLE skill_evidences (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id         UUID         NOT NULL REFERENCES users(id)      ON DELETE CASCADE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    skill_category  VARCHAR(100) NOT NULL,
    ai_summary      TEXT         NOT NULL,
    developer_notes TEXT,
    source_events   TEXT,
    reviewed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_skill_evidences_user            ON skill_evidences(user_id);
CREATE INDEX idx_skill_evidences_workspace_user  ON skill_evidences(workspace_id, user_id);
CREATE INDEX idx_skill_evidences_user_status     ON skill_evidences(user_id, status);
