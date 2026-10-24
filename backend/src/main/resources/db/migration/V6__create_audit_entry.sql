-- Who changed what in the dashboard, kept without a time limit. The actor's name and avatar and the description are
-- snapshots taken at the time of the change, so an entry stays readable after the admin, the rule or the role is gone.
-- The backend only ever inserts rows.
CREATE TABLE audit_entry
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at      TIMESTAMPTZ  NOT NULL,
    -- `discord:<id>` or `admin:<username>`, like the subject of the session.
    actor_subject    VARCHAR(255) NOT NULL,
    actor_name       VARCHAR(255) NOT NULL,
    actor_avatar_url VARCHAR(512),
    action           VARCHAR(64)  NOT NULL,
    description      TEXT         NOT NULL
);

-- The dashboard pages through the newest entries first, optionally for one admin or one action.
CREATE INDEX audit_entry_actor ON audit_entry (actor_subject, id DESC);
CREATE INDEX audit_entry_action ON audit_entry (action, id DESC);
