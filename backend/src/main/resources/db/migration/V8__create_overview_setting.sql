-- Settings of the dashboard's start page; a single row, written once an admin picks a country. The name is a snapshot,
-- so the start page can name the country while WarEra cannot be reached.
CREATE TABLE overview_setting
(
    id           SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    country_id   VARCHAR(64)  NOT NULL,
    country_name VARCHAR(255) NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL
);
