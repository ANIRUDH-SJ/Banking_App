-- Microsoft Authenticator compatible TOTP credential storage.
-- Run after 08_identity_security_extensions.sql.
CREATE TABLE user_totp (
    user_id            NUMBER(19) NOT NULL,
    secret_ciphertext  VARCHAR2(2048 CHAR) NOT NULL,
    is_enabled         CHAR(1 CHAR) DEFAULT 'N' NOT NULL,
    confirmed_at       TIMESTAMP(6),
    created_at         TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at         TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_user_totp PRIMARY KEY (user_id),
    CONSTRAINT fk_user_totp_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_user_totp_enabled CHECK (is_enabled IN ('Y', 'N'))
);

CREATE OR REPLACE TRIGGER trg_user_totp_set_updated_at
BEFORE UPDATE ON user_totp
FOR EACH ROW
BEGIN
    :NEW.updated_at := SYSTIMESTAMP;
END;
/
