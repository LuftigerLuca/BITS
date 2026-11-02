-- The language members chose for what BITS writes only to them. Kept by Discord ID, since it works without a link;
-- no row means automatic. Deleted when they go back to automatic or leave the guild.
CREATE TABLE language_preference
(
    discord_user_id VARCHAR(32) PRIMARY KEY,
    language        VARCHAR(16) NOT NULL CHECK (language IN ('GERMAN', 'ENGLISH')),
    updated_at      TIMESTAMPTZ NOT NULL
);
