-- The forum channels of the guild as the bot reported them last, with their tags; TOP forums are picked from them.
CREATE TABLE discord_forum
(
    id                 VARCHAR(32)  PRIMARY KEY,
    name               VARCHAR(100) NOT NULL,
    category           VARCHAR(100),
    position           INTEGER      NOT NULL,
    -- Whether the bot may lock, archive and tag the posts there and write in them.
    threads_manageable BOOLEAN      NOT NULL
);

CREATE TABLE discord_forum_tag
(
    forum_id VARCHAR(32)  NOT NULL REFERENCES discord_forum (id) ON DELETE CASCADE,
    position INTEGER      NOT NULL,
    tag_id   VARCHAR(32)  NOT NULL,
    name     VARCHAR(100) NOT NULL,
    PRIMARY KEY (forum_id, position)
);

-- Forums whose posts are agenda items (TOPs) that /topclose and /topopen work in. No reference to discord_forum, which
-- every report replaces; a forum that disappears in Discord stays here until an admin saves the setting again.
CREATE TABLE top_forum
(
    forum_id    VARCHAR(32) PRIMARY KEY,
    -- The tag a closed TOP gets; NULL for none.
    done_tag_id VARCHAR(32)
);
