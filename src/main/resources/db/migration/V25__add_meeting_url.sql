-- V25: Add optional meeting_url to meetings (Story: custom join link)
ALTER TABLE meetings ADD COLUMN meeting_url VARCHAR(2048);
