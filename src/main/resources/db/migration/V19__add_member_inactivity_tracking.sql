-- Story 8.2: Track last time an inactivity alert was sent for each workspace member
-- This prevents duplicate MEMBER_INACTIVE notifications within the 24h cooldown window.
ALTER TABLE workspace_members
    ADD COLUMN last_inactivity_alerted_at TIMESTAMPTZ;
