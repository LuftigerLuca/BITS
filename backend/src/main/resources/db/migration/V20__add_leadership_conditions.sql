-- Conditions on leading a military unit and on chairing a party. The military unit and the party are optional for
-- them: without, any military unit or any party of the country counts.
ALTER TABLE role_rule_condition
    ADD COLUMN mu_role VARCHAR(32);

ALTER TABLE role_rule_condition
    DROP CONSTRAINT role_rule_condition_fields;
ALTER TABLE role_rule_condition
    ADD CONSTRAINT role_rule_condition_fields CHECK (
        CASE type
            WHEN 'CITIZENSHIP' THEN
                country_id IS NOT NULL AND target_name IS NOT NULL
                    AND office IS NULL AND mu_id IS NULL AND threshold IS NULL AND party_id IS NULL AND mu_role IS NULL
            WHEN 'GOVERNMENT_OFFICE' THEN
                country_id IS NOT NULL AND target_name IS NOT NULL
                    AND office IS NOT NULL AND office IN ('PRESIDENT', 'VICE_PRESIDENT', 'MINISTER_OF_ECONOMY',
                                                          'MINISTER_OF_DEFENSE', 'MINISTER_OF_FOREIGN_AFFAIRS', 'CONGRESS')
                    AND mu_id IS NULL AND threshold IS NULL AND party_id IS NULL AND mu_role IS NULL
            WHEN 'MILITARY_UNIT' THEN
                mu_id IS NOT NULL AND target_name IS NOT NULL
                    AND country_id IS NULL AND office IS NULL AND threshold IS NULL AND party_id IS NULL AND mu_role IS NULL
            WHEN 'LEVEL' THEN
                threshold IS NOT NULL AND threshold >= 1
                    AND country_id IS NULL AND office IS NULL AND mu_id IS NULL AND party_id IS NULL AND mu_role IS NULL
            WHEN 'MILITARY_RANK' THEN
                threshold IS NOT NULL AND threshold >= 1
                    AND country_id IS NULL AND office IS NULL AND mu_id IS NULL AND party_id IS NULL AND mu_role IS NULL
            WHEN 'COMPANIES' THEN
                threshold IS NOT NULL AND threshold >= 1
                    AND country_id IS NULL AND office IS NULL AND mu_id IS NULL AND party_id IS NULL AND mu_role IS NULL
            WHEN 'PARTY' THEN
                country_id IS NOT NULL AND party_id IS NOT NULL AND target_name IS NOT NULL
                    AND office IS NULL AND mu_id IS NULL AND threshold IS NULL AND mu_role IS NULL
            WHEN 'MU_LEADERSHIP' THEN
                mu_role IS NOT NULL AND mu_role IN ('COMMANDER', 'MANAGER', 'OWNER')
                    -- The name of the military unit, if one is set.
                    AND (mu_id IS NULL) = (target_name IS NULL)
                    AND country_id IS NULL AND office IS NULL AND threshold IS NULL AND party_id IS NULL
            WHEN 'PARTY_LEADER' THEN
                -- The name of the party, or of the country without a party.
                country_id IS NOT NULL AND target_name IS NOT NULL
                    AND office IS NULL AND mu_id IS NULL AND threshold IS NULL AND mu_role IS NULL
            ELSE FALSE
        END
    );

-- As in V17, with the rank.
DROP INDEX role_rule_condition_once;
CREATE UNIQUE INDEX role_rule_condition_once
    ON role_rule_condition (rule_id, type, country_id, office, mu_id, threshold, party_id, mu_role) NULLS NOT DISTINCT;
