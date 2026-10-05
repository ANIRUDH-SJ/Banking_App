ALTER TABLE otp_verification DROP CONSTRAINT ck_otp_purpose;
ALTER TABLE otp_verification ADD CONSTRAINT ck_otp_purpose
    CHECK (purpose IN ('LOGIN', 'FUND_TRANSFER', 'BILL_PAYMENT',
                      'BENEFICIARY_ACTIVATION', 'FOREX_CONVERSION', 'PASSWORD_RESET'));
