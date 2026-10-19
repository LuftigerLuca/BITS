CREATE TABLE ping_log
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    discord_user_id VARCHAR(32) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL
);
