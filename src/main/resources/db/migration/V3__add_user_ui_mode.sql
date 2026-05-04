-- V3: Add ui_mode preference to users table
ALTER TABLE users
    ADD COLUMN ui_mode VARCHAR(10) NOT NULL DEFAULT 'CHARACTER'
        CHECK (ui_mode IN ('CHARACTER', 'SERIOUS'));
