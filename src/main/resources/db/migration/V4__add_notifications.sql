CREATE TABLE notifications
(
    id           UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    workspace_id UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    type         VARCHAR(50) NOT NULL,
    payload      TEXT        NOT NULL     DEFAULT '{}',
    is_read      BOOLEAN     NOT NULL     DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL     DEFAULT now()
);

CREATE INDEX idx_notifications_user_id ON notifications (user_id);
CREATE INDEX idx_notifications_user_id_is_read ON notifications (user_id, is_read) WHERE is_read = false;
