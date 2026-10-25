-- The vote ends early once more than threshold_percent of the eligible members chose threshold_choice (YES or NO); set
-- at the start, both NULL for votes that end only at their end date or by hand.
ALTER TABLE vote
    ADD COLUMN threshold_percent INTEGER CHECK (threshold_percent BETWEEN 1 AND 99),
    ADD COLUMN threshold_choice  VARCHAR(16) CHECK (threshold_choice IN ('YES', 'NO')),
    ADD CONSTRAINT vote_threshold_complete CHECK ((threshold_percent IS NULL) = (threshold_choice IS NULL));
