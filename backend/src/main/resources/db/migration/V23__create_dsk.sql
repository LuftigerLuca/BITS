-- DSKs: groups of German military units whose linked members get the role of their DSK. A role belongs to one DSK at
-- most; that it has no role rule as well is up to the backend, since the rules live in another module.
CREATE TABLE dsk
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    role_id    VARCHAR(32) UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX dsk_name_unique ON dsk (lower(name));

-- The military units of the DSKs; each belongs to one DSK at most. The name is a snapshot taken when it was assigned.
CREATE TABLE dsk_military_unit
(
    mu_id       VARCHAR(64)  PRIMARY KEY,
    dsk_id      BIGINT       NOT NULL REFERENCES dsk (id) ON DELETE CASCADE,
    mu_name     VARCHAR(255) NOT NULL,
    assigned_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX dsk_military_unit_dsk ON dsk_military_unit (dsk_id);
