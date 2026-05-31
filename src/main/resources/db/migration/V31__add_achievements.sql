CREATE TABLE ticket_tags (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id   UUID        NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    tag         VARCHAR(50) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (ticket_id, tag)
);

CREATE INDEX idx_ticket_tags_ticket ON ticket_tags (ticket_id);

CREATE TABLE user_achievements (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL,
    workspace_id      UUID,
    achievement_key   VARCHAR(50) NOT NULL,
    earned_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    evidence_snapshot JSONB,
    UNIQUE (user_id, achievement_key)
);

CREATE INDEX idx_user_achievements_user ON user_achievements (user_id);
