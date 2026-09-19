-- Tables owned by the web module. Kept separate from the emulator's own schema (which the auth-server
-- creates with hibernate ddl-auto=update) so upstream migrations never collide with ours.

CREATE TABLE IF NOT EXISTS web_account_role (
    account_id BIGINT      NOT NULL PRIMARY KEY,
    role       VARCHAR(16) NOT NULL,
    granted_by VARCHAR(64) NULL,
    created    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- C = our own cash currency (1 THB = 10 C). The game knows nothing about it; it only ever sees the AP
-- we convert C into, 1 C = 1 AP.
CREATE TABLE IF NOT EXISTS web_cash_balance (
    account_id BIGINT    NOT NULL PRIMARY KEY,
    balance_c  INT       NOT NULL DEFAULT 0,
    modified   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Every movement of C, append only. `ref` is UNIQUE: that is what stops the same TrueMoney voucher
-- from being credited twice, even if two requests race.
CREATE TABLE IF NOT EXISTS web_cash_ledger (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT       NOT NULL,
    delta_c    INT          NOT NULL,
    kind       VARCHAR(16)  NOT NULL,
    ref        VARCHAR(128) NULL,
    detail     VARCHAR(255) NULL,
    created    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cash_ledger_ref (ref),
    KEY idx_cash_ledger_account (account_id, id)
);

-- Raw log of every TrueMoney call (verify + redeem, success or not). The API is unofficial and can
-- change shape without warning, so keep what it actually answered.
CREATE TABLE IF NOT EXISTS web_tmgift_check (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id   BIGINT       NULL,
    voucher_hash VARCHAR(128) NOT NULL,
    step         VARCHAR(16)  NOT NULL,
    outcome      VARCHAR(32)  NOT NULL,
    amount_baht  INT          NULL,
    raw          TEXT         NULL,
    created      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_tmgift_hash (voucher_hash),
    KEY idx_tmgift_account (account_id, id)
);
