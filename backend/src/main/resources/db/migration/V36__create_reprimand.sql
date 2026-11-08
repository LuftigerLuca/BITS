-- Reprimands given with /reprimand. They never expire; a withdrawn one stays with withdrawn_at set and no longer counts.
-- They stay when the member leaves the server, or leaving would reset the count.
CREATE TABLE reprimand
(
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    discord_user_id       VARCHAR(32)  NOT NULL,
    -- Names as Discord showed them at the time.
    member_name           VARCHAR(100) NOT NULL,
    reason                VARCHAR(500) NOT NULL,
    actor_discord_user_id VARCHAR(32)  NOT NULL,
    actor_name            VARCHAR(100) NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    -- End of the timeout it brought; NULL if the bot could not set it.
    timeout_until         TIMESTAMPTZ,
    withdrawn_at          TIMESTAMPTZ,
    withdrawn_by_name     VARCHAR(100)
);

CREATE INDEX reprimand_discord_user_id_idx ON reprimand (discord_user_id);
CREATE INDEX reprimand_actor_discord_user_id_idx ON reprimand (actor_discord_user_id);

-- Roles whose members may reprimand, besides the admins.
CREATE TABLE reprimand_role
(
    role_id VARCHAR(32) PRIMARY KEY
);

-- The timeout every reprimand brings; at most one row, without it 10 minutes.
CREATE TABLE reprimand_setting
(
    id              SMALLINT PRIMARY KEY,
    timeout_minutes INTEGER     NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

