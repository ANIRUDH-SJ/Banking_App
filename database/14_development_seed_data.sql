-- Internet Net Banking Application
-- DEVELOPMENT-ONLY seed data. All names, addresses, account numbers, and tokens are fictional.
--
-- Prerequisite: run schema scripts 00 through 13 first.
-- This script is idempotent: it will not duplicate the demo records when run again.
-- Never run it in production. Do not add real customer data, real cards, passwords, OTPs, or TOTP secrets.
-- otp_verification and user_totp are intentionally not seeded: OTPs expire and TOTP secrets are encrypted
-- using each developer's local encryption key. Enroll Microsoft Authenticator through the API instead.
--
-- Demo users created by this script use the password: password
-- Change or remove these accounts before any non-development deployment.
-- Re-running this script resets the three demo-user passwords to this documented value.

SET ECHO ON
SET FEEDBACK ON
WHENEVER SQLERROR EXIT SQL.SQLCODE

-- This BCrypt value is for the development-only password "password".
MERGE INTO app_user target
USING (
    SELECT 'demo_admin' username, 'demo.admin@example.test' email FROM dual
    UNION ALL SELECT 'demo_customer_1', 'demo.customer1@example.test' FROM dual
    UNION ALL SELECT 'demo_customer_2', 'demo.customer2@example.test' FROM dual
) source
ON (target.username = source.username)
WHEN MATCHED THEN
    UPDATE SET target.password_hash = '$2a$10$s776mUmI7yEaa1Dmt5ItyOOnS51RU2IrFyigBIXL8mkcOTzw51s5.',
               target.account_status = 'ACTIVE',
               target.failed_login_attempts = 0,
               target.locked_until = NULL
WHEN NOT MATCHED THEN
    INSERT (username, email, password_hash, account_status, failed_login_attempts)
    VALUES (source.username, source.email,
            '$2a$10$s776mUmI7yEaa1Dmt5ItyOOnS51RU2IrFyigBIXL8mkcOTzw51s5.',
            'ACTIVE', 0);

MERGE INTO user_role target
USING (
    SELECT u.user_id, r.role_id
    FROM app_user u CROSS JOIN role r
    WHERE (u.username = 'demo_admin' AND r.role_code = 'ADMIN')
       OR (u.username IN ('demo_customer_1', 'demo_customer_2') AND r.role_code = 'CUSTOMER')
) source
ON (target.user_id = source.user_id AND target.role_id = source.role_id)
WHEN NOT MATCHED THEN
    INSERT (user_id, role_id) VALUES (source.user_id, source.role_id);

MERGE INTO bank target
USING (SELECT 'DEMO' bank_code, 'Demo Internet Bank Limited' legal_name,
              'Demo Internet Bank' display_name FROM dual) source
ON (target.bank_code = source.bank_code)
WHEN NOT MATCHED THEN
    INSERT (bank_code, legal_name, display_name)
    VALUES (source.bank_code, source.legal_name, source.display_name);

MERGE INTO branch target
USING (
    SELECT (SELECT bank_id FROM bank WHERE bank_code = 'DEMO') bank_id,
           'BLR001' branch_code, 'Demo Bengaluru Main Branch' branch_name,
           'DEMO0BLR001' ifsc_code, 'blr.branch@example.test' email,
           '+91-0000000001' phone_number, '100 Demo Avenue' address_line_1,
           'Technology Park' address_line_2, 'Bengaluru' city, 'Karnataka' state,
           '560001' postal_code
    FROM dual
) source
ON (target.bank_id = source.bank_id AND target.branch_code = source.branch_code)
WHEN NOT MATCHED THEN
    INSERT (bank_id, branch_code, branch_name, ifsc_code, email, phone_number,
            address_line_1, address_line_2, city, state, postal_code)
    VALUES (source.bank_id, source.branch_code, source.branch_name, source.ifsc_code,
            source.email, source.phone_number, source.address_line_1, source.address_line_2,
            source.city, source.state, source.postal_code);

MERGE INTO customer target
USING (
    SELECT (SELECT user_id FROM app_user WHERE username = 'demo_customer_1') user_id,
           'DEMO-CUST-001' customer_number, 'Aarav' first_name, 'Demo' last_name,
           DATE '1994-05-15' date_of_birth, '+91-0000001001' mobile_number FROM dual
    UNION ALL
    SELECT (SELECT user_id FROM app_user WHERE username = 'demo_customer_2'),
           'DEMO-CUST-002', 'Diya', 'Sample', DATE '1996-09-22', '+91-0000001002' FROM dual
) source
ON (target.user_id = source.user_id)
WHEN NOT MATCHED THEN
    INSERT (user_id, customer_number, first_name, last_name, date_of_birth, mobile_number,
            kyc_status, kyc_verified_at)
    VALUES (source.user_id, source.customer_number, source.first_name, source.last_name,
            source.date_of_birth, source.mobile_number, 'VERIFIED', SYSTIMESTAMP);

MERGE INTO customer_address target
USING (
    SELECT (SELECT customer_id FROM customer WHERE customer_number = 'DEMO-CUST-001') customer_id,
           'RESIDENTIAL' address_type, '101 Sample Street' address_line_1,
           'Demo Layout' address_line_2, 'Bengaluru' city, 'Karnataka' state, '560001' postal_code
    FROM dual
    UNION ALL
    SELECT (SELECT customer_id FROM customer WHERE customer_number = 'DEMO-CUST-002'),
           'RESIDENTIAL', '202 Example Road', 'Test Nagar', 'Bengaluru', 'Karnataka', '560002' FROM dual
) source
ON (target.customer_id = source.customer_id AND target.address_type = source.address_type)
WHEN NOT MATCHED THEN
    INSERT (customer_id, address_type, address_line_1, address_line_2, city, state, postal_code, is_primary)
    VALUES (source.customer_id, source.address_type, source.address_line_1, source.address_line_2,
            source.city, source.state, source.postal_code, 'Y');

MERGE INTO bank_account target
USING (
    SELECT (SELECT branch_id FROM branch WHERE ifsc_code = 'DEMO0BLR001') branch_id,
           '700000000001' account_number, 21500 current_balance, 21500 available_balance FROM dual
    UNION ALL
    SELECT (SELECT branch_id FROM branch WHERE ifsc_code = 'DEMO0BLR001'),
           '700000000002', 12500, 12500 FROM dual
) source
ON (target.account_number = source.account_number)
WHEN NOT MATCHED THEN
    INSERT (branch_id, account_number, account_type, currency_code, account_status,
            current_balance, available_balance)
    VALUES (source.branch_id, source.account_number, 'SAVINGS', 'INR', 'ACTIVE',
            source.current_balance, source.available_balance);

MERGE INTO account_holder target
USING (
    SELECT (SELECT account_id FROM bank_account WHERE account_number = '700000000001') account_id,
           (SELECT customer_id FROM customer WHERE customer_number = 'DEMO-CUST-001') customer_id FROM dual
    UNION ALL
    SELECT (SELECT account_id FROM bank_account WHERE account_number = '700000000002'),
           (SELECT customer_id FROM customer WHERE customer_number = 'DEMO-CUST-002') FROM dual
) source
ON (target.account_id = source.account_id AND target.customer_id = source.customer_id)
WHEN NOT MATCHED THEN
    INSERT (account_id, customer_id, holder_type, is_active)
    VALUES (source.account_id, source.customer_id, 'PRIMARY', 'Y');

MERGE INTO beneficiary target
USING (
    SELECT (SELECT customer_id FROM customer WHERE customer_number = 'DEMO-CUST-001') customer_id,
           'Diya Savings' nickname, 'Diya Sample' beneficiary_name,
           '700000000002' account_number, 'DEMO0BLR001' ifsc_code,
           'Demo Internet Bank' bank_name FROM dual
) source
ON (target.customer_id = source.customer_id AND target.nickname = source.nickname)
WHEN NOT MATCHED THEN
    INSERT (customer_id, nickname, beneficiary_name, account_number, ifsc_code, bank_name,
            beneficiary_status, activated_at)
    VALUES (source.customer_id, source.nickname, source.beneficiary_name, source.account_number,
            source.ifsc_code, source.bank_name, 'ACTIVE', SYSTIMESTAMP);

MERGE INTO bank_transaction target
USING (
    SELECT 'DEMO-TXN-TRANSFER-001' transaction_reference,
           (SELECT account_id FROM bank_account WHERE account_number = '700000000001') debit_account_id,
           (SELECT account_id FROM bank_account WHERE account_number = '700000000002') credit_account_id,
           (SELECT beneficiary_id FROM beneficiary WHERE nickname = 'Diya Savings'
             AND customer_id = (SELECT customer_id FROM customer WHERE customer_number = 'DEMO-CUST-001')) beneficiary_id,
           (SELECT user_id FROM app_user WHERE username = 'demo_customer_1') initiated_by_user_id,
           'TRANSFER' transaction_type, 1000 amount, 'Demo transfer to Diya' narration
    FROM dual
    UNION ALL
    SELECT 'DEMO-TXN-LOAN-001',
           (SELECT account_id FROM bank_account WHERE account_number = '700000000001'),
           NULL, NULL, (SELECT user_id FROM app_user WHERE username = 'demo_customer_1'),
           'LOAN_PAYMENT', 2500, 'Demo personal-loan repayment' FROM dual
) source
ON (target.transaction_reference = source.transaction_reference)
WHEN NOT MATCHED THEN
    INSERT (transaction_reference, debit_account_id, credit_account_id, beneficiary_id,
            initiated_by_user_id, transaction_type, transaction_status, amount, currency_code,
            narration, completed_at)
    VALUES (source.transaction_reference, source.debit_account_id, source.credit_account_id,
            source.beneficiary_id, source.initiated_by_user_id, source.transaction_type,
            'COMPLETED', source.amount, 'INR', source.narration, SYSTIMESTAMP);

MERGE INTO account_transaction_entry target
USING (
    SELECT (SELECT transaction_id FROM bank_transaction WHERE transaction_reference = 'DEMO-TXN-TRANSFER-001') transaction_id,
           (SELECT account_id FROM bank_account WHERE account_number = '700000000001') account_id,
           'DEBIT' entry_type, 1000 amount, 24000 balance_after FROM dual
    UNION ALL
    SELECT (SELECT transaction_id FROM bank_transaction WHERE transaction_reference = 'DEMO-TXN-TRANSFER-001'),
           (SELECT account_id FROM bank_account WHERE account_number = '700000000002'), 'CREDIT', 1000, 12500 FROM dual
    UNION ALL
    SELECT (SELECT transaction_id FROM bank_transaction WHERE transaction_reference = 'DEMO-TXN-LOAN-001'),
           (SELECT account_id FROM bank_account WHERE account_number = '700000000001'), 'DEBIT', 2500, 21500 FROM dual
) source
ON (target.transaction_id = source.transaction_id AND target.account_id = source.account_id
    AND target.entry_type = source.entry_type)
WHEN NOT MATCHED THEN
    INSERT (transaction_id, account_id, entry_type, amount, balance_after)
    VALUES (source.transaction_id, source.account_id, source.entry_type, source.amount, source.balance_after);

MERGE INTO bank_transaction_status_history target
USING (
    SELECT (SELECT transaction_id FROM bank_transaction WHERE transaction_reference = 'DEMO-TXN-TRANSFER-001') transaction_id,
           'PROCESSING' previous_status, 'COMPLETED' new_status,
           (SELECT user_id FROM app_user WHERE username = 'demo_customer_1') changed_by_user_id FROM dual
    UNION ALL
    SELECT (SELECT transaction_id FROM bank_transaction WHERE transaction_reference = 'DEMO-TXN-LOAN-001'),
           'PROCESSING', 'COMPLETED', (SELECT user_id FROM app_user WHERE username = 'demo_customer_1') FROM dual
) source
ON (target.transaction_id = source.transaction_id AND target.new_status = source.new_status)
WHEN NOT MATCHED THEN
    INSERT (transaction_id, previous_status, new_status, changed_by_user_id)
    VALUES (source.transaction_id, source.previous_status, source.new_status, source.changed_by_user_id);

MERGE INTO bank_card target
USING (
    SELECT (SELECT customer_id FROM customer WHERE customer_number = 'DEMO-CUST-001') customer_id,
           (SELECT account_id FROM bank_account WHERE account_number = '700000000001') account_id,
           'demo-token-000000000001' card_token, '4242' last_four FROM dual
) source
ON (target.card_token = source.card_token)
WHEN NOT MATCHED THEN
    INSERT (customer_id, account_id, card_token, last_four, card_type, card_network,
            expiry_month, expiry_year, card_status, activated_at)
    VALUES (source.customer_id, source.account_id, source.card_token, source.last_four,
            'DEBIT', 'VISA', 12, 2030, 'ACTIVE', SYSTIMESTAMP);

MERGE INTO loan_account target
USING (
    SELECT (SELECT customer_id FROM customer WHERE customer_number = 'DEMO-CUST-001') customer_id,
           'DEMOLOAN000001' loan_account_number FROM dual
) source
ON (target.loan_account_number = source.loan_account_number)
WHEN NOT MATCHED THEN
    INSERT (customer_id, loan_account_number, loan_type, principal_amount, outstanding_principal,
            interest_rate, term_months, emi_amount, currency_code, disbursed_on, next_due_date,
            maturity_date, loan_status)
    VALUES (source.customer_id, source.loan_account_number, 'PERSONAL', 100000, 97500,
            10.5000, 24, 4600, 'INR', DATE '2026-01-15', DATE '2026-10-15',
            DATE '2028-01-15', 'ACTIVE');

MERGE INTO loan_payment target
USING (
    SELECT (SELECT loan_id FROM loan_account WHERE loan_account_number = 'DEMOLOAN000001') loan_id,
           (SELECT account_id FROM bank_account WHERE account_number = '700000000001') source_account_id,
           (SELECT transaction_id FROM bank_transaction WHERE transaction_reference = 'DEMO-TXN-LOAN-001') transaction_id
    FROM dual
) source
ON (target.transaction_id = source.transaction_id)
WHEN NOT MATCHED THEN
    INSERT (loan_id, source_account_id, transaction_id, transaction_reference, idempotency_key,
            amount, currency_code, outstanding_after, payment_status)
    VALUES (source.loan_id, source.source_account_id, source.transaction_id,
            'DEMO-TXN-LOAN-001', 'demo-loan-payment-001', 2500, 'INR', 97500, 'COMPLETED');

INSERT INTO login_audit (user_id, username_attempted, login_outcome, client_ip_address, user_agent)
SELECT u.user_id, u.username, 'SUCCESS', '127.0.0.1', 'Development seed data'
FROM app_user u
WHERE u.username = 'demo_customer_1'
  AND NOT EXISTS (
      SELECT 1 FROM login_audit l
      WHERE l.user_id = u.user_id AND l.user_agent = 'Development seed data'
  );

INSERT INTO audit_event (user_id, event_type, entity_type, entity_id, outcome, event_details)
SELECT u.user_id, 'DEMO_DATA_SEEDED', 'CUSTOMER', TO_CHAR(c.customer_id), 'SUCCESS',
       TO_CLOB('Development-only synthetic data created.')
FROM app_user u JOIN customer c ON c.user_id = u.user_id
WHERE u.username = 'demo_customer_1'
  AND NOT EXISTS (
      SELECT 1 FROM audit_event a
      WHERE a.user_id = u.user_id AND a.event_type = 'DEMO_DATA_SEEDED'
  );

INSERT INTO notification (user_id, notification_type, title, message, is_read)
SELECT u.user_id, 'ACCOUNT', 'Welcome to Demo Internet Banking',
       'This is synthetic development data for local testing only.', 'N'
FROM app_user u
WHERE u.username = 'demo_customer_1'
  AND NOT EXISTS (
      SELECT 1 FROM notification n
      WHERE n.user_id = u.user_id AND n.title = 'Welcome to Demo Internet Banking'
  );

INSERT INTO notification_delivery (notification_id, channel, delivery_status, attempted_at)
SELECT n.notification_id, 'IN_APP', 'SENT', SYSTIMESTAMP
FROM notification n JOIN app_user u ON u.user_id = n.user_id
WHERE u.username = 'demo_customer_1'
  AND n.title = 'Welcome to Demo Internet Banking'
  AND NOT EXISTS (
      SELECT 1 FROM notification_delivery d
      WHERE d.notification_id = n.notification_id AND d.channel = 'IN_APP'
  );

COMMIT;

PROMPT Development seed data created successfully.
PROMPT Row counts after seeding:

SELECT table_name, row_count
FROM (
    SELECT 'ROLE' table_name, COUNT(*) row_count FROM role
    UNION ALL SELECT 'APP_USER', COUNT(*) FROM app_user
    UNION ALL SELECT 'USER_ROLE', COUNT(*) FROM user_role
    UNION ALL SELECT 'BANK', COUNT(*) FROM bank
    UNION ALL SELECT 'BRANCH', COUNT(*) FROM branch
    UNION ALL SELECT 'CUSTOMER', COUNT(*) FROM customer
    UNION ALL SELECT 'CUSTOMER_ADDRESS', COUNT(*) FROM customer_address
    UNION ALL SELECT 'BANK_ACCOUNT', COUNT(*) FROM bank_account
    UNION ALL SELECT 'ACCOUNT_HOLDER', COUNT(*) FROM account_holder
    UNION ALL SELECT 'BENEFICIARY', COUNT(*) FROM beneficiary
    UNION ALL SELECT 'BANK_TRANSACTION', COUNT(*) FROM bank_transaction
    UNION ALL SELECT 'ACCOUNT_TRANSACTION_ENTRY', COUNT(*) FROM account_transaction_entry
    UNION ALL SELECT 'BANK_TRANSACTION_STATUS_HISTORY', COUNT(*) FROM bank_transaction_status_history
    UNION ALL SELECT 'LOGIN_AUDIT', COUNT(*) FROM login_audit
    UNION ALL SELECT 'AUDIT_EVENT', COUNT(*) FROM audit_event
    UNION ALL SELECT 'OTP_VERIFICATION (NOT SEEDED)', COUNT(*) FROM otp_verification
    UNION ALL SELECT 'USER_TOTP (NOT SEEDED)', COUNT(*) FROM user_totp
    UNION ALL SELECT 'NOTIFICATION', COUNT(*) FROM notification
    UNION ALL SELECT 'NOTIFICATION_DELIVERY', COUNT(*) FROM notification_delivery
    UNION ALL SELECT 'BANK_CARD', COUNT(*) FROM bank_card
    UNION ALL SELECT 'LOAN_ACCOUNT', COUNT(*) FROM loan_account
    UNION ALL SELECT 'LOAN_PAYMENT', COUNT(*) FROM loan_payment
)
ORDER BY table_name;

WHENEVER SQLERROR CONTINUE NONE
