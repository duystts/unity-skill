-- Story 9.2: Granular tracking permissions per repository and channel.
-- No record = tracking enabled (default-permit model).
-- resource_type: GITHUB_REPO | CHAT_CHANNEL
-- resource_id: 'owner/repo' for GITHUB_REPO; projectId UUID string for CHAT_CHANNEL
CREATE TABLE tracking_permissions (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workspace_id  UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    resource_type VARCHAR(20) NOT NULL,
    resource_id   VARCHAR(255) NOT NULL,
    enabled       BOOLEAN     NOT NULL DEFAULT true,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, workspace_id, resource_type, resource_id)
);

CREATE INDEX idx_tracking_permissions_lookup
    ON tracking_permissions(user_id, workspace_id, resource_type, resource_id);
