-- V8: Add last_alerted_at to tickets for inactivity alert deduplication
ALTER TABLE tickets ADD COLUMN last_alerted_at TIMESTAMPTZ;
