-- Roles of the guild as last reported by the bot; replaced as a whole on every report.
CREATE TABLE discord_role
(
    id         VARCHAR(32)  NOT NULL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    color      INTEGER,
    position   INTEGER      NOT NULL,
    assignable BOOLEAN      NOT NULL
);

-- A Discord role and exactly one condition; several rules for the same role are combined with OR. No foreign key to
-- discord_role, since the rules of a deleted role are kept and shown as orphaned.
CREATE TABLE role_rule
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role_id     VARCHAR(32)  NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    country_id  VARCHAR(64),
    office      VARCHAR(32),
    mu_id       VARCHAR(64),
    threshold   INTEGER,
    -- Name of the country or military unit at the time the rule was created.
    target_name VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX role_rule_role_id ON role_rule (role_id);
