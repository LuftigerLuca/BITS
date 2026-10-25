-- How the result image of a vote looks; a single row, written once an admin changes something. background is the
-- uploaded picture, already scaled down and stored as JPEG, NULL for the eagle that comes with the backend. Colours are
-- 0xRRGGBB, NULL for the palette's.
CREATE TABLE vote_result_setting
(
    id             SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    background     BYTEA,
    no_party_color INTEGER CHECK (no_party_color BETWEEN 0 AND 16777215),
    updated_at     TIMESTAMPTZ NOT NULL
);

-- The colour of a WarEra party in the result image, by WarEra's party ID, so a renamed party keeps it. Only those of
-- the country set for the dashboard count, so parties of other countries keep their palette colour.
CREATE TABLE vote_party_color
(
    party_id   VARCHAR(64) PRIMARY KEY,
    country_id VARCHAR(64) NOT NULL,
    color      INTEGER     NOT NULL CHECK (color BETWEEN 0 AND 16777215)
);

CREATE INDEX vote_party_color_country ON vote_party_color (country_id);

-- The WarEra party a result belongs to, so archived votes get the colours too; NULL for people in no party and for
-- results from before this column.
ALTER TABLE vote_party_result
    ADD COLUMN party_id VARCHAR(64);
