-- V28: ticket codes
-- Each project gets a short key prefix (e.g. "US", "PROJ")
-- Each ticket gets a sequential number within its project (1, 2, 3 ...)
-- Developers name their PRs: [PREFIX-N] description
-- The system uses this to detect whether a ticket already has a PR.

-- Project key prefix (auto-generated from project name, editable later)
ALTER TABLE projects
    ADD COLUMN key_prefix VARCHAR(10) NOT NULL DEFAULT 'PROJ';

-- Sequential ticket number per project (1-based)
ALTER TABLE tickets
    ADD COLUMN ticket_number INT NOT NULL DEFAULT 0;

-- Backfill BEFORE adding the unique constraint
-- (all rows start at 0 — must assign real numbers first)
UPDATE tickets t
SET ticket_number = sub.rn
FROM (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY project_id ORDER BY created_at ASC) AS rn
    FROM tickets
) sub
WHERE t.id = sub.id;

-- Now safe to enforce uniqueness (all values are 1-based and distinct per project)
ALTER TABLE tickets
    ADD CONSTRAINT uq_tickets_project_number UNIQUE (project_id, ticket_number);
