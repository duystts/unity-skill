-- Story 7.6: Away periods for streak protection
CREATE TABLE away_periods (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL,
    workspace_id UUID,
    start_date   DATE        NOT NULL,
    end_date     DATE        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_away_period_dates CHECK (end_date >= start_date)
);

CREATE INDEX idx_away_periods_user ON away_periods(user_id);
