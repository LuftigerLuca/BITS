-- Features an admin switched under "Verwaltung". A feature without a row is on, so a new one starts on.
CREATE TABLE feature_setting
(
    feature    VARCHAR(32) PRIMARY KEY,
    enabled    BOOLEAN     NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
