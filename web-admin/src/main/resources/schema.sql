-- Tables owned by the web module. Kept separate from the emulator's own schema (which the auth-server
-- creates with hibernate ddl-auto=update) so upstream migrations never collide with ours.

CREATE TABLE IF NOT EXISTS web_account_role (
    account_id BIGINT      NOT NULL PRIMARY KEY,
    role       VARCHAR(16) NOT NULL,
    granted_by VARCHAR(64) NULL,
    created    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
