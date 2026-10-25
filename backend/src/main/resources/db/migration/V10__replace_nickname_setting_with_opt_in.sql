-- Members opt in to their WarEra name as nickname themselves, so the switch for the whole server is gone.
DROP TABLE nickname_setting;

-- Members who asked BITS to set their nickname to their WarEra name. Kept by Discord ID, so it lasts across new links;
-- deleted when they turn it off or leave the guild.
CREATE TABLE nickname_opt_in
(
    discord_user_id VARCHAR(32) PRIMARY KEY,
    opted_in_at     TIMESTAMPTZ NOT NULL
);
