-- V9: Add chat_messages for project-scoped group chat (Story 5.1)
CREATE TABLE chat_messages (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    project_id   UUID        NOT NULL REFERENCES projects(id)   ON DELETE CASCADE,
    sender_id    UUID                 REFERENCES users(id)      ON DELETE SET NULL,
    content      TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL  DEFAULT now()
);
CREATE INDEX idx_chat_messages_workspace_project
    ON chat_messages(workspace_id, project_id, created_at DESC);
