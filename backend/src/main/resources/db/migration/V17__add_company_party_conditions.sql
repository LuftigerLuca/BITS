-- Conditions on the number of companies and on the party.
ALTER TABLE role_rule_condition
    ADD COLUMN party_id VARCHAR(64);

ALTER TABLE role_rule_condition
    DROP CONSTRAINT role_rule_condition_fields;
ALTER TABLE role_rule_condition
    ADD CONSTRAINT role_rule_condition_fields CHECK (
        CASE type
            WHEN 'CITIZENSHIP' THEN
                country_id IS NOT NULL AND target_name IS NOT NULL
                    AND office IS NULL AND mu_id IS NULL AND threshold IS NULL AND party_id IS NULL
            WHEN 'GOVERNMENT_OFFICE' THEN
                country_id IS NOT NULL AND target_name IS NOT NULL
                    AND office IS NOT NULL AND office IN ('PRESIDENT', 'VICE_PRESIDENT', 'MINISTER_OF_ECONOMY',
                                                          'MINISTER_OF_DEFENSE', 'MINISTER_OF_FOREIGN_AFFAIRS', 'CONGRESS')
                    AND mu_id IS NULL AND threshold IS NULL AND party_id IS NULL
            WHEN 'MILITARY_UNIT' THEN
                mu_id IS NOT NULL AND target_name IS NOT NULL
                    AND country_id IS NULL AND office IS NULL AND threshold IS NULL AND party_id IS NULL
            WHEN 'LEVEL' THEN
                threshold IS NOT NULL AND threshold >= 1
                    AND country_id IS NULL AND office IS NULL AND mu_id IS NULL AND party_id IS NULL
            WHEN 'MILITARY_RANK' THEN
                threshold IS NOT NULL AND threshold >= 1
                    AND country_id IS NULL AND office IS NULL AND mu_id IS NULL AND party_id IS NULL
            WHEN 'COMPANIES' THEN
                threshold IS NOT NULL AND threshold >= 1
                    AND country_id IS NULL AND office IS NULL AND mu_id IS NULL AND party_id IS NULL
            WHEN 'PARTY' THEN
                country_id IS NOT NULL AND party_id IS NOT NULL AND target_name IS NOT NULL
                    AND office IS NULL AND mu_id IS NULL AND threshold IS NULL
            ELSE FALSE
        END
    );

-- As in V16, with the party.
DROP INDEX role_rule_condition_once;
CREATE UNIQUE INDEX role_rule_condition_once
    ON role_rule_condition (rule_id, type, country_id, office, mu_id, threshold, party_id) NULLS NOT DISTINCT;
