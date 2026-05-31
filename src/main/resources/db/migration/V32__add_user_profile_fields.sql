-- Extended profile fields for Account Settings page
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS title       VARCHAR(100),
    ADD COLUMN IF NOT EXISTS timezone    VARCHAR(60)  DEFAULT 'Asia/Ho_Chi_Minh',
    ADD COLUMN IF NOT EXISTS bio         TEXT;
