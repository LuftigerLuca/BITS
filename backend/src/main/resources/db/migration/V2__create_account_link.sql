-- 1 Discord : 1 WarEra, so both sides are unique.
CREATE TABLE account_link
(
    discord_user_id VARCHAR(32)  NOT NULL PRIMARY KEY,
    warera_user_id  VARCHAR(64)  NOT NULL UNIQUE,
    warera_username VARCHAR(255) NOT NULL,
    verified_at     TIMESTAMPTZ  NOT NULL
);

-- At most one open code per Discord account; a new verification replaces the row.
CREATE TABLE account_verification
(
    discord_user_id VARCHAR(32)  NOT NULL PRIMARY KEY,
    warera_user_id  VARCHAR(64)  NOT NULL,
    warera_username VARCHAR(255) NOT NULL,
    code            VARCHAR(16)  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    last_checked_at TIMESTAMPTZ
);
