-- Story 8.3: PM-configurable workload thresholds per workspace.
-- Defaults match the hardcoded values from Story 8.1 WorkloadStatus.fromOpenTicketCount(int).
ALTER TABLE workspaces
    ADD COLUMN overloaded_threshold   INT NOT NULL DEFAULT 5,
    ADD COLUMN balanced_min_threshold INT NOT NULL DEFAULT 2;
