-- A government office condition may name several offices, of which holding one is enough. They are stored in one
-- column, sorted by rank and separated by commas; every existing condition keeps its office as the only one, so its
-- label and conditions_key stay as they are.
ALTER TABLE role_rule_condition
    ADD COLUMN offices VARCHAR(255);
UPDATE role_rule_condition
SET offices = office;

ALTER TABLE role_rule_condition
    DROP CONSTRAINT role_rule_condition_fields;
DROP INDEX role_rule_condition_once;
ALTER TABLE role_rule_condition
    DROP COLUMN office;

ALTER TABLE role_rule_condition
    ADD CONSTRAINT role_rule_condition_fields CHECK (
        CASE type
            WHEN 'CITIZENSHIP' THEN
                country_id IS NOT NULL AND target_name IS NOT NULL
                    AND offices IS NULL AND mu_id IS NULL AND threshold IS NULL AND party_id IS NULL AND mu_role IS NULL
            WHEN 'GOVERNMENT_OFFICE' THEN
                country_id IS NOT NULL AND target_name IS NOT NULL
                    -- Every name an office; sorting them by rank without repeats is up to the backend.
                    AND offices IS NOT NULL AND offices <> ''
                    AND string_to_array(offices, ',') <@ ARRAY ['PRESIDENT', 'VICE_PRESIDENT', 'MINISTER_OF_ECONOMY',
                                                                'MINISTER_OF_DEFENSE', 'MINISTER_OF_FOREIGN_AFFAIRS', 'CONGRESS']
                    AND mu_id IS NULL AND threshold IS NULL AND party_id IS NULL AND mu_role IS NULL
            WHEN 'MILITARY_UNIT' THEN
                mu_id IS NOT NULL AND target_name IS NOT NULL
                    AND country_id IS NULL AND offices IS NULL AND threshold IS NULL AND party_id IS NULL AND mu_role IS NULL
            WHEN 'LEVEL' THEN
                threshold IS NOT NULL AND threshold >= 1
                    AND country_id IS NULL AND offices IS NULL AND mu_id IS NULL AND party_id IS NULL AND mu_role IS NULL
            WHEN 'MILITARY_RANK' THEN
                threshold IS NOT NULL AND threshold >= 1
                    AND country_id IS NULL AND offices IS NULL AND mu_id IS NULL AND party_id IS NULL AND mu_role IS NULL
            WHEN 'COMPANIES' THEN
                threshold IS NOT NULL AND threshold >= 1
                    AND country_id IS NULL AND offices IS NULL AND mu_id IS NULL AND party_id IS NULL AND mu_role IS NULL
            WHEN 'PARTY' THEN
                country_id IS NOT NULL AND party_id IS NOT NULL AND target_name IS NOT NULL
                    AND offices IS NULL AND mu_id IS NULL AND threshold IS NULL AND mu_role IS NULL
            WHEN 'MU_LEADERSHIP' THEN
                mu_role IS NOT NULL AND mu_role IN ('COMMANDER', 'MANAGER', 'OWNER')
                    -- The name of the military unit, if one is set.
                    AND (mu_id IS NULL) = (target_name IS NULL)
                    AND country_id IS NULL AND offices IS NULL AND threshold IS NULL AND party_id IS NULL
            WHEN 'PARTY_LEADER' THEN
                -- The name of the party, or of the country without a party.
                country_id IS NOT NULL AND target_name IS NOT NULL
                    AND offices IS NULL AND mu_id IS NULL AND threshold IS NULL AND mu_role IS NULL
            ELSE FALSE
        END
    );

-- As in V20, with the offices.
CREATE UNIQUE INDEX role_rule_condition_once
    ON role_rule_condition (rule_id, type, country_id, offices, mu_id, threshold, party_id, mu_role) NULLS NOT DISTINCT;
