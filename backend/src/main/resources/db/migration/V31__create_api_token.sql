-- Bearer tokens of the third-party API under /external/v1. Only the SHA-256 hash of a token is kept; the token itself
-- is shown once, when it is created. Revoking a token deletes its row, the audit log keeps the record.
CREATE TABLE api_token
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    -- Hex of SHA-256; tokens are random, so no salt is needed.
    token_hash      VARCHAR(64)  NOT NULL UNIQUE,
    -- The start of the token, so admins can tell tokens apart.
    token_hint      VARCHAR(16)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by_name VARCHAR(100) NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    -- Updated at most once a minute.
    last_used_at    TIMESTAMPTZ
);

-- The endpoints a token may call, by the ID of their scope; fixed when the token is created.
CREATE TABLE api_token_scope
(
    token_id BIGINT      NOT NULL REFERENCES api_token (id) ON DELETE CASCADE,
    scope    VARCHAR(64) NOT NULL,
    PRIMARY KEY (token_id, scope)
);
