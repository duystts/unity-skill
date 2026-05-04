-- Story 8.1: Team health snapshot table for future analytics/caching
CREATE TABLE team_health_snapshots (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  UUID        NOT NULL,
    snapshot_date DATE        NOT NULL,
    metrics_json  JSONB       NOT NULL DEFAULT '{}',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_team_health_workspace ON team_health_snapshots(workspace_id);
CREATE INDEX idx_team_health_date ON team_health_snapshots(snapshot_date);
