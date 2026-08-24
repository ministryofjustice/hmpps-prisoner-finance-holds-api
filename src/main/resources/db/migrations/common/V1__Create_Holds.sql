CREATE TABLE holds
(
    id                 UUID                        NOT NULL,
    prison_number      VARCHAR(255)                NOT NULL,
    legacy_hold_number BIGINT                      NOT NULL,
    sub_account_ref    VARCHAR(255)                NOT NULL,
    created_at         TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_by         VARCHAR(255)                NOT NULL,
    hold_from_date     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    hold_until_date    TIMESTAMP WITHOUT TIME ZONE,
    is_released        BOOLEAN                     NOT NULL,
    description        VARCHAR(255),
    hold_type          VARCHAR(255)                NOT NULL,
    amount             BIGINT                      NOT NULL,
    CONSTRAINT pk_holds PRIMARY KEY (id)
);

ALTER TABLE holds
    ADD CONSTRAINT uc_holds_legacy_hold_number UNIQUE (legacy_hold_number);