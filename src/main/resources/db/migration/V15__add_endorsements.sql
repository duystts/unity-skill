-- Story 7.4: Peer endorsements for published skill evidence
CREATE TABLE endorsements (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    evidence_id  UUID         NOT NULL REFERENCES skill_evidences(id) ON DELETE CASCADE,
    endorser_id  UUID         NOT NULL,
    workspace_id UUID         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_endorsement UNIQUE (evidence_id, endorser_id)
);

CREATE INDEX idx_endorsements_evidence ON endorsements(evidence_id);
CREATE INDEX idx_endorsements_endorser ON endorsements(endorser_id);
