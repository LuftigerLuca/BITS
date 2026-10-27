-- Status and activity of the bot in Discord; a single row, written once an admin sets them. Until then the bot is
-- online without activity.
CREATE TABLE bot_presence_setting
(
    id            SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    status        VARCHAR(32) NOT NULL,
    activity_type VARCHAR(32),
    activity_text VARCHAR(128),
    updated_at    TIMESTAMPTZ NOT NULL,
    -- An activity has both a type and a text, or neither.
    CHECK ((activity_type IS NULL) = (activity_text IS NULL))
);
