CREATE TABLE ticket_attachments (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id           UUID         NOT NULL,
    project_id          UUID         NOT NULL,
    workspace_id        UUID         NOT NULL,
    uploader_id         UUID         NOT NULL,
    file_name           VARCHAR(255) NOT NULL,
    cloudinary_public_id VARCHAR(500) NOT NULL,
    url                 TEXT         NOT NULL,
    resource_type       VARCHAR(20)  NOT NULL,  -- image | video | raw
    bytes               BIGINT       NOT NULL DEFAULT 0,
    format              VARCHAR(20),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_ticket_attachments_ticket    ON ticket_attachments (ticket_id);
CREATE INDEX idx_ticket_attachments_workspace ON ticket_attachments (workspace_id);
