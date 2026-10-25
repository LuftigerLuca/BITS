-- The private threads of the guild as the bot reported them last; admins pick embassy threads from them.
CREATE TABLE discord_thread
(
    id                  VARCHAR(32)  PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    parent_channel_id   VARCHAR(32)  NOT NULL,
    parent_channel_name VARCHAR(100) NOT NULL,
    archived            BOOLEAN      NOT NULL,
    manageable          BOOLEAN      NOT NULL
);

-- The embassy thread of a foreign country, one per country. The names are snapshots taken when it was assigned, so
-- the dashboard can still name a thread that is gone.
CREATE TABLE embassy_thread
(
    thread_id         VARCHAR(32)  PRIMARY KEY,
    thread_name       VARCHAR(100) NOT NULL,
    parent_channel_id VARCHAR(32)  NOT NULL,
    country_id        VARCHAR(64)  NOT NULL UNIQUE,
    country_name      VARCHAR(255) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL
);

-- Members an admin added to a thread by hand; they stay regardless of any office.
CREATE TABLE embassy_manual_member
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    thread_id       VARCHAR(32)  NOT NULL REFERENCES embassy_thread (thread_id) ON DELETE CASCADE,
    discord_user_id VARCHAR(32)  NOT NULL,
    -- As the dashboard showed the member when they were added.
    member_name     VARCHAR(100),
    added_at        TIMESTAMPTZ  NOT NULL,
    UNIQUE (thread_id, discord_user_id)
);

-- Who is in each thread and why, as the bot reported after the last sync.
CREATE TABLE embassy_thread_member
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    thread_id       VARCHAR(32)  NOT NULL REFERENCES embassy_thread (thread_id) ON DELETE CASCADE,
    discord_user_id VARCHAR(32)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    reason          VARCHAR(255) NOT NULL,
    UNIQUE (thread_id, discord_user_id)
);

-- The government offices whose holders belong in the thread of their country, by GovernmentOffice name.
CREATE TABLE embassy_office
(
    office VARCHAR(32) PRIMARY KEY
);

INSERT INTO embassy_office (office)
VALUES ('PRESIDENT'),
       ('VICE_PRESIDENT'),
       ('MINISTER_OF_FOREIGN_AFFAIRS');

-- Roles whose members belong in every embassy thread, e.g. the own government.
CREATE TABLE embassy_host_role
(
    role_id VARCHAR(32) PRIMARY KEY
);

-- The ambassador role; a single row, missing while none is set.
CREATE TABLE embassy_setting
(
    id                 SMALLINT    PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    ambassador_role_id VARCHAR(32),
    updated_at         TIMESTAMPTZ NOT NULL
);
