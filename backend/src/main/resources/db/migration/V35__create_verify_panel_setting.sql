-- The channel in which the bot keeps a message with a button to verify, and that message; a single row, written once
-- there is something to keep.
CREATE TABLE verify_panel_setting
(
    id                 SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    -- NULL while no channel is set.
    channel_id         VARCHAR(32),
    -- Where the message is; differs from channel_id until the bot has moved it.
    message_channel_id VARCHAR(32),
    message_id         VARCHAR(32),
    updated_at         TIMESTAMPTZ NOT NULL
);
