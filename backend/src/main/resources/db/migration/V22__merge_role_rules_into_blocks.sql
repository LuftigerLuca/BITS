-- Each role has one rule at most, made of blocks of conditions: the conditions of a block must all be met, of the
-- blocks one is enough. The rules of a role so far were combined with OR, so each becomes a block of the oldest rule
-- of its role, in the order they were created; the conditions keep their order within a block. Every member gets the
-- same roles as before.
ALTER TABLE role_rule_condition
    ADD COLUMN block INTEGER NOT NULL DEFAULT 0 CHECK (block >= 0);
ALTER TABLE role_rule_condition
    ALTER COLUMN block DROP DEFAULT;

-- Postgres checks these row by row, so moving the conditions would clash with them halfway.
ALTER TABLE role_rule_condition
    DROP CONSTRAINT role_rule_condition_pkey;
DROP INDEX role_rule_condition_once;

WITH moved AS (
    SELECT c.rule_id,
           c.position,
           first_value(r.id) OVER (PARTITION BY r.role_id ORDER BY r.created_at, r.id)                AS new_rule_id,
           dense_rank() OVER (PARTITION BY r.role_id ORDER BY r.created_at, r.id) - 1                 AS new_block,
           row_number() OVER (PARTITION BY r.role_id ORDER BY r.created_at, r.id, c.position) - 1     AS new_position
    FROM role_rule_condition c
             JOIN role_rule r ON r.id = c.rule_id
)
UPDATE role_rule_condition c
SET rule_id  = moved.new_rule_id,
    block    = moved.new_block,
    position = moved.new_position
FROM moved
WHERE c.rule_id = moved.rule_id
  AND c.position = moved.position;

-- Their conditions moved to the oldest rule of the role.
DELETE FROM role_rule newer
    USING role_rule oldest
WHERE oldest.role_id = newer.role_id
  AND (oldest.created_at, oldest.id) < (newer.created_at, newer.id);

ALTER TABLE role_rule_condition
    ADD PRIMARY KEY (rule_id, position);
-- As in V21, per block: the same condition may stand in several blocks, but only once in each. The same block twice
-- is up to the backend to catch.
CREATE UNIQUE INDEX role_rule_condition_once
    ON role_rule_condition (rule_id, block, type, country_id, offices, mu_id, threshold, party_id, mu_role) NULLS NOT DISTINCT;

-- One rule per role makes the key of the conditions unnecessary; the rules of the same role used to differ in it.
DROP INDEX role_rule_conditions_unique;
ALTER TABLE role_rule
    DROP COLUMN conditions_key;
CREATE UNIQUE INDEX role_rule_role_unique ON role_rule (role_id);
