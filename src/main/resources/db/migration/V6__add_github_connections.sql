CREATE TABLE github_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    repo_full_name VARCHAR(255),
    encrypted_oauth_token TEXT,
    webhook_secret VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_github_connections_project ON github_connections(project_id);
CREATE INDEX idx_github_connections_workspace ON github_connections(workspace_id);
