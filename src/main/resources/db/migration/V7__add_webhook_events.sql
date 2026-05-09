CREATE TYPE webhook_status AS ENUM ('RECEIVED', 'PROCESSED', 'FAILED');

CREATE TABLE webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL,
    project_id UUID NOT NULL,
    repo_full_name VARCHAR(255),
    event_type VARCHAR(100) NOT NULL,
    payload_json TEXT NOT NULL,
    status webhook_status NOT NULL DEFAULT 'RECEIVED',
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_events_workspace ON webhook_events(workspace_id);
CREATE INDEX idx_webhook_events_status ON webhook_events(status);
