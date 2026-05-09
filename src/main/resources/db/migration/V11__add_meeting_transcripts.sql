-- V11: Add meeting_transcripts table for transcript upload (Story 5.4)
CREATE TABLE meeting_transcripts (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id   UUID         NOT NULL REFERENCES meetings(id)    ON DELETE CASCADE,
    workspace_id UUID         NOT NULL REFERENCES workspaces(id)  ON DELETE CASCADE,
    raw_content  TEXT         NOT NULL,
    status       VARCHAR(50)  NOT NULL DEFAULT 'UPLOADED',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_meeting_transcripts_meeting ON meeting_transcripts(meeting_id);
