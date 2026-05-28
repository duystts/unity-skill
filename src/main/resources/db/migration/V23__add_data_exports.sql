-- Story 9.4: Stores asynchronous data export jobs per user.
-- status: IN_PROGRESS (export running) | READY (download available)
CREATE TABLE data_exports (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status       VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    export_json  TEXT,                          -- null until status=READY
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ                    -- null until status=READY
);

CREATE INDEX idx_data_exports_user_status ON data_exports(user_id, status);
