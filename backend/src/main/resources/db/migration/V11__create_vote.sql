-- Roles whose members may start, end and cancel votes, besides the admins.
CREATE TABLE vote_manager_role
(
    role_id VARCHAR(32) PRIMARY KEY
);

-- The text channels of the guild as the bot reported them last; votes are posted in one of them.
CREATE TABLE discord_channel
(
    id       VARCHAR(32)  PRIMARY KEY,
    name     VARCHAR(100) NOT NULL,
    category VARCHAR(100),
    position INTEGER      NOT NULL,
    postable BOOLEAN      NOT NULL
);

-- A yes/no/abstain vote among the members of a role. The names of role, channel and the people who started or closed
-- it are snapshots, so the archive stays readable after they are gone.
CREATE TABLE vote
(
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    question               VARCHAR(500) NOT NULL,
    role_id                VARCHAR(32)  NOT NULL,
    role_name              VARCHAR(100),
    channel_id             VARCHAR(32)  NOT NULL,
    channel_name           VARCHAR(100),
    -- The bot's message with the buttons, once posted.
    message_id             VARCHAR(32),
    -- OPEN, COUNTING (ended, result not yet there), EVALUATED or CANCELLED.
    state                  VARCHAR(16)  NOT NULL,
    started_at             TIMESTAMPTZ  NOT NULL,
    started_by_name        VARCHAR(255) NOT NULL,
    -- NULL for a vote that runs until somebody ends it.
    ends_at                TIMESTAMPTZ,
    closed_at              TIMESTAMPTZ,
    -- NULL if it ended on its own.
    closed_by_name         VARCHAR(255),
    -- Participation as of the bot's last report, for the dashboard.
    eligible_count         INTEGER,
    electorate_reported_at TIMESTAMPTZ,
    -- Last time the backend tried to get the result out: evaluation or another request to the bot.
    last_attempt_at        TIMESTAMPTZ,
    -- Whether a failed evaluation is in the system log already, so it is recorded once per vote.
    problem_recorded       BOOLEAN      NOT NULL DEFAULT FALSE,
    evaluated_at           TIMESTAMPTZ,
    result_posted_at       TIMESTAMPTZ
);

CREATE INDEX vote_state ON vote (state);

-- Who voted what, only while the vote runs and is counted; deleted with the evaluation or cancellation. The WarEra
-- account is the one linked at the time of voting.
CREATE TABLE vote_ballot
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vote_id         BIGINT      NOT NULL REFERENCES vote (id) ON DELETE CASCADE,
    discord_user_id VARCHAR(32) NOT NULL,
    warera_user_id  VARCHAR(64) NOT NULL,
    choice          VARCHAR(16) NOT NULL,
    cast_at         TIMESTAMPTZ NOT NULL,
    UNIQUE (vote_id, discord_user_id)
);

-- The members of the role when the vote ended, as the bot reported them; deleted with the evaluation.
CREATE TABLE vote_elector
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vote_id         BIGINT       NOT NULL REFERENCES vote (id) ON DELETE CASCADE,
    discord_user_id VARCHAR(32)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    UNIQUE (vote_id, discord_user_id)
);

-- The result per WarEra party, which is all that is kept of the votes. party_name NULL stands for people in no party.
CREATE TABLE vote_party_result
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vote_id        BIGINT       NOT NULL REFERENCES vote (id) ON DELETE CASCADE,
    position       INTEGER      NOT NULL,
    party_name     VARCHAR(255),
    yes_count      INTEGER      NOT NULL,
    no_count       INTEGER      NOT NULL,
    abstain_count  INTEGER      NOT NULL,
    eligible_count INTEGER      NOT NULL,
    UNIQUE (vote_id, position)
);

-- Members who could have voted and did not, by name only.
CREATE TABLE vote_non_voter
(
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vote_id  BIGINT       NOT NULL REFERENCES vote (id) ON DELETE CASCADE,
    position INTEGER      NOT NULL,
    name     VARCHAR(100) NOT NULL,
    UNIQUE (vote_id, position)
);
