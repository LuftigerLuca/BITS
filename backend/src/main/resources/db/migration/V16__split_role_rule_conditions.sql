-- A rule holds one or more conditions, all of which must be met; several rules for the same role stay OR. Every
-- existing rule becomes a rule with exactly its one condition, so it behaves as before.
CREATE TABLE role_rule_condition
(
    rule_id     BIGINT       NOT NULL REFERENCES role_rule (id) ON DELETE CASCADE,
    position    INTEGER      NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    country_id  VARCHAR(64),
    office      VARCHAR(32),
    mu_id       VARCHAR(64),
    threshold   INTEGER,
    -- Name of the country or military unit at the time the rule was created.
    target_name VARCHAR(255),
    PRIMARY KEY (rule_id, position),
    -- As role_rule_condition in V5.
    CONSTRAINT role_rule_condition_fields CHECK (
        CASE type
            WHEN 'CITIZENSHIP' THEN
                country_id IS NOT NULL AND target_name IS NOT NULL
                    AND office IS NULL AND mu_id IS NULL AND threshold IS NULL
            WHEN 'GOVERNMENT_OFFICE' THEN
                country_id IS NOT NULL AND target_name IS NOT NULL
                    AND office IS NOT NULL AND office IN ('PRESIDENT', 'VICE_PRESIDENT', 'MINISTER_OF_ECONOMY',
                                                          'MINISTER_OF_DEFENSE', 'MINISTER_OF_FOREIGN_AFFAIRS', 'CONGRESS')
                    AND mu_id IS NULL AND threshold IS NULL
            WHEN 'MILITARY_UNIT' THEN
                mu_id IS NOT NULL AND target_name IS NOT NULL
                    AND country_id IS NULL AND office IS NULL AND threshold IS NULL
            WHEN 'LEVEL' THEN
                threshold IS NOT NULL AND threshold >= 1 AND country_id IS NULL AND office IS NULL AND mu_id IS NULL
            WHEN 'MILITARY_RANK' THEN
                threshold IS NOT NULL AND threshold >= 1 AND country_id IS NULL AND office IS NULL AND mu_id IS NULL
            ELSE FALSE
        END
    )
);

INSERT INTO role_rule_condition (rule_id, position, type, country_id, office, mu_id, threshold, target_name)
SELECT id, 0, type, country_id, office, mu_id, threshold, target_name
FROM role_rule;

-- The same condition twice in one rule changes nothing.
CREATE UNIQUE INDEX role_rule_condition_once
    ON role_rule_condition (rule_id, type, country_id, office, mu_id, threshold) NULLS NOT DISTINCT;

-- The conditions of a rule in a canonical form, so the same set of conditions twice for one role is caught by an
-- index, like the single condition was before. It must match RoleRule.conditionsKey: per condition the fields joined
-- with '|', NULL as empty, the conditions sorted and joined with ';'. The existing rules have one condition each.
ALTER TABLE role_rule
    ADD COLUMN conditions_key VARCHAR(2000);
UPDATE role_rule
SET conditions_key = concat(type, '|', coalesce(country_id, ''), '|', coalesce(office, ''), '|', coalesce(mu_id, ''), '|',
                            coalesce(threshold::TEXT, ''));
ALTER TABLE role_rule
    ALTER COLUMN conditions_key SET NOT NULL;

ALTER TABLE role_rule
    DROP CONSTRAINT role_rule_condition;
DROP INDEX role_rule_condition_unique;
ALTER TABLE role_rule
    DROP COLUMN type,
    DROP COLUMN country_id,
    DROP COLUMN office,
    DROP COLUMN mu_id,
    DROP COLUMN threshold,
    DROP COLUMN target_name;

-- Starts with role_id, so it also serves the lookups by role.
CREATE UNIQUE INDEX role_rule_conditions_unique ON role_rule (role_id, conditions_key);
