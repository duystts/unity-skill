-- V2: workspace_invitations — email and link-based member invitations

CREATE TABLE workspace_invitations (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    invited_by   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email        VARCHAR(255),
    token        VARCHAR(64) NOT NULL UNIQUE,
    type         VARCHAR(10) NOT NULL CHECK (type IN ('EMAIL', 'LINK')),
    status       VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    expires_at   TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_workspace_invitations_token        ON workspace_invitations(token);
CREATE INDEX idx_workspace_invitations_workspace_id ON workspace_invitations(workspace_id);
CREATE INDEX idx_workspace_invitations_email        ON workspace_invitations(email) WHERE email IS NOT NULL;
