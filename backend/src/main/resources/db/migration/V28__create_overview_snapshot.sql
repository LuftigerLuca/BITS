-- The start page's key figures once a day (Europe/Berlin), for their history; only numbers. A figure that could not
-- be read all day (the bot had not reported the members, WarEra was down, no country was set) stays NULL, a gap, not
-- 0. The country the citizens belong to, so a later switch does not mix two countries in one line.
CREATE TABLE overview_snapshot
(
    day              DATE PRIMARY KEY,
    members          INTEGER,
    verified_members INTEGER,
    country_id       VARCHAR(64),
    citizens         INTEGER,
    active_citizens  INTEGER
);
