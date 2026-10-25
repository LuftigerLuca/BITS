-- The participation in percent at which a vote ends early, set at the start; NULL for votes that end only at their end
-- date or by hand.
ALTER TABLE vote
    ADD COLUMN auto_close_percent INTEGER CHECK (auto_close_percent BETWEEN 1 AND 100);
