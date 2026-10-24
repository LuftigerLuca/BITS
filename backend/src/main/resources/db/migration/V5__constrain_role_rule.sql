-- Which condition fields a rule has depends on its type, and its label needs the name of the country or MU. So far
-- only RoleRuleService checked this; a rule without them broke the whole sync. Rules created through the dashboard
-- always had the right fields. Every branch tests its fields with IS NOT NULL, since a CHECK that comes out NULL
-- counts as met.
ALTER TABLE role_rule
    ADD CONSTRAINT role_rule_condition CHECK (
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
    );

-- The same condition twice for one role changes nothing but shows up twice in the dashboard. Duplicates created so
-- far are dropped, keeping the oldest.
DELETE FROM role_rule duplicate
    USING role_rule original
WHERE duplicate.id > original.id
  AND duplicate.role_id = original.role_id
  AND duplicate.type = original.type
  AND duplicate.country_id IS NOT DISTINCT FROM original.country_id
  AND duplicate.office IS NOT DISTINCT FROM original.office
  AND duplicate.mu_id IS NOT DISTINCT FROM original.mu_id
  AND duplicate.threshold IS NOT DISTINCT FROM original.threshold;

-- Replaces the plain index on role_id, which this one starts with.
DROP INDEX role_rule_role_id;
CREATE UNIQUE INDEX role_rule_condition_unique
    ON role_rule (role_id, type, country_id, office, mu_id, threshold) NULLS NOT DISTINCT;
