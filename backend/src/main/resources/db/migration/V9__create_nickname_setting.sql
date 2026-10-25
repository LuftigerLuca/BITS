-- Whether the bot sets the nicknames of linked members to their WarEra names; a single row, written once an admin
-- changes it. Without it, BITS leaves nicknames alone.
CREATE TABLE nickname_setting
(
    id         SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    enabled    BOOLEAN     NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Nicknames the bot set or cleared in a sync run, once it reported back; skipped and failed ones count as such.
ALTER TABLE system_log_entry
    ADD COLUMN nickname_count INTEGER;
