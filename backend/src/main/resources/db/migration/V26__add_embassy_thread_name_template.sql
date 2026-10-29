-- The name of the embassy threads the bot creates, with {land} for the English name of the country as WarEra has it.
ALTER TABLE embassy_setting
    ADD COLUMN thread_name_template VARCHAR(100) NOT NULL DEFAULT 'Botschaft {land}';
