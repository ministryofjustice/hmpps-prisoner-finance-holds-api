
ALTER TABLE holds
    ADD COLUMN prisoner_subaccount_uuid UUID NULL,
    ADD COLUMN prison_subaccount_uuid UUID NULL;