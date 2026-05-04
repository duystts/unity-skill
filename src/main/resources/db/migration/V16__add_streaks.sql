-- Story 7.5: Contribution streak tracking per developer per workspace
CREATE TABLE contribution_streaks (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID        NOT NULL,
    workspace_id         UUID        NOT NULL,
    current_streak_weeks INT         NOT NULL DEFAULT 0,
    longest_streak_weeks INT         NOT NULL DEFAULT 0,
    last_activity_week   DATE        NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_streak UNIQUE (user_id, workspace_id)
);

CREATE INDEX idx_streaks_user ON contribution_streaks(user_id);
