-- Notification preferences stored as JSONB on the user.
-- Default: all enabled. Shape: {"ticketAssigned":true,"achievementEarned":true,"skillEvidence":true,"inviteAccepted":true}
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS notification_prefs JSONB DEFAULT '{"ticketAssigned":true,"achievementEarned":true,"skillEvidence":true,"inviteAccepted":true}'::jsonb;
