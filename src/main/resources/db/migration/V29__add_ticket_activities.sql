CREATE TABLE ticket_activities (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id     UUID        NOT NULL,
    project_id    UUID        NOT NULL,
    workspace_id  UUID        NOT NULL,
    actor_id      UUID,                          -- null = system/automation
    actor_name    VARCHAR(255),                  -- denormalized display name
    type          VARCHAR(50) NOT NULL,          -- TICKET_CREATED | STAGE_CHANGED | PR_LINKED | TICKET_ASSIGNED
    from_stage_id UUID,                          -- STAGE_CHANGED only
    to_stage_id   UUID,                          -- STAGE_CHANGED only
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ticket_activities_project ON ticket_activities (project_id, created_at DESC);
CREATE INDEX idx_ticket_activities_ticket  ON ticket_activities (ticket_id,  created_at DESC);
