-- V5: projects, workflow_stages, auto_trigger_rules, tickets
-- Note: architecture planned these as V2, but V2-V4 were used for workspace features

CREATE TABLE projects (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id   UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name           VARCHAR(100) NOT NULL,
    description    TEXT,
    visibility     VARCHAR(10)  NOT NULL DEFAULT 'PRIVATE'
                   CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    github_repo_id BIGINT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_projects_workspace_id ON projects(workspace_id);

CREATE TABLE workflow_stages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    position        INTEGER      NOT NULL DEFAULT 0,
    is_closed_state BOOLEAN      NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_workflow_stages_project_id ON workflow_stages(project_id);

CREATE TABLE auto_trigger_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    workspace_id    UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    trigger_type    VARCHAR(50) NOT NULL,
    source_stage_id UUID REFERENCES workflow_stages(id) ON DELETE CASCADE,
    target_stage_id UUID        NOT NULL REFERENCES workflow_stages(id) ON DELETE CASCADE
);
CREATE INDEX idx_auto_trigger_rules_project_id ON auto_trigger_rules(project_id);

CREATE TABLE tickets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    project_id      UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    stage_id        UUID REFERENCES workflow_stages(id) ON DELETE SET NULL,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    assignee_id     UUID REFERENCES users(id) ON DELETE SET NULL,
    assignment_mode VARCHAR(20)  NOT NULL DEFAULT 'NONE'
                    CHECK (assignment_mode IN ('NONE', 'ASSIGNED', 'OPEN_POOL')),
    github_pr_url   VARCHAR(512),
    closed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_tickets_workspace_id ON tickets(workspace_id);
CREATE INDEX idx_tickets_project_id   ON tickets(project_id);
CREATE INDEX idx_tickets_stage_id     ON tickets(stage_id);
CREATE INDEX idx_tickets_assignee_id  ON tickets(assignee_id);
