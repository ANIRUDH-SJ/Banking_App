-- ============================================================================
-- Oracle International Bank local development seed
-- ============================================================================
-- Run only after every service has completed its Flyway migrations.
-- Connect to FREEPDB1 as SYS/SYSDBA (or an account with access to all NB_*
-- schemas) and run this file with SQL Developer's Run Script command (F5).
--
-- This is intentionally a LOCAL/DEVELOPMENT reset for the three seed customers.
-- It is rerunnable and uses reserved identifiers in the 900000+ range.
--
-- Customer logins (password for all three: password):
--   seed_customer_01 / anirudhsj2004@gmail.com
--   seed_customer_02 / anirudhsj2004+seed02@gmail.com
--   seed_customer_03 / anirudhsj2004+seed03@gmail.com
--
-- Gmail delivers the +seed02 and +seed03 aliases to anirudhsj2004@gmail.com.
-- Separate aliases are required because application emails are unique and an
-- email address is also a login identifier.
-- ============================================================================

SET DEFINE OFF
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

PROMPT === Identity: three usable customer profiles ===

-- Free the three development addresses if an older local account owns one.
UPDATE nb_identity.app_user
SET email = 'retired-' || user_id || '@local.invalid'
WHERE LOWER(email) IN (
        'anirudhsj2004@gmail.com',
        'anirudhsj2004+seed02@gmail.com',
        'anirudhsj2004+seed03@gmail.com')
  AND user_id NOT IN (900001, 900003, 900004);

DECLARE
    v_user_id         NUMBER(19);
    v_username        VARCHAR2(100);
    v_email           VARCHAR2(254);
    v_first_name      VARCHAR2(100);
    v_last_name       VARCHAR2(100);
    v_mobile          VARCHAR2(30);
    v_address         VARCHAR2(200);
    v_city            VARCHAR2(100);
    v_postal_code     VARCHAR2(20);
    v_customer_number VARCHAR2(20);
BEGIN
    FOR customer_index IN 1..3 LOOP
        v_user_id := CASE customer_index WHEN 1 THEN 900001 WHEN 2 THEN 900003 ELSE 900004 END;
        v_username := 'seed_customer_0' || customer_index;
        v_email := CASE customer_index
            WHEN 1 THEN 'anirudhsj2004@gmail.com'
            WHEN 2 THEN 'anirudhsj2004+seed02@gmail.com'
            ELSE 'anirudhsj2004+seed03@gmail.com'
        END;
        v_first_name := CASE customer_index WHEN 1 THEN 'Anirudh' WHEN 2 THEN 'Priya' ELSE 'Rohan' END;
        v_last_name := CASE customer_index WHEN 1 THEN 'Jahagirdar' WHEN 2 THEN 'Sharma' ELSE 'Mehta' END;
        v_mobile := CASE customer_index
            WHEN 1 THEN '+919986100429'
            WHEN 2 THEN '+919845012347'
            ELSE '+919900876541'
        END;
        v_address := CASE customer_index
            WHEN 1 THEN '42, 12th Main Road, Indiranagar'
            WHEN 2 THEN '18, 5th Cross, Jayanagar'
            ELSE '77, Lake View Road, Whitefield'
        END;
        v_city := 'Bengaluru';
        v_postal_code := CASE customer_index WHEN 1 THEN '560038' WHEN 2 THEN '560041' ELSE '560066' END;
        v_customer_number := 'CUST' || LPAD(TO_CHAR(v_user_id), 16, '0');

        MERGE INTO nb_identity.app_user target
        USING (SELECT v_user_id user_id, v_username username, v_email email FROM dual) source
        ON (target.user_id = source.user_id)
        WHEN MATCHED THEN UPDATE SET
            target.username = source.username,
            target.email = source.email,
            target.password_hash = '$2a$10$70VTwQ3HElEKnj8yP53SduYk/Tafqic..Ln3t6PQcYTEaO4C5oeJy',
            target.account_status = 'ACTIVE',
            target.failed_login_attempts = 0,
            target.locked_until = NULL,
            target.updated_at = SYSTIMESTAMP
        WHEN NOT MATCHED THEN INSERT (
            user_id, username, email, password_hash, account_status,
            failed_login_attempts, password_changed_at, created_at, updated_at)
        VALUES (
            source.user_id, source.username, source.email,
            '$2a$10$70VTwQ3HElEKnj8yP53SduYk/Tafqic..Ln3t6PQcYTEaO4C5oeJy',
            'ACTIVE', 0, SYSTIMESTAMP, SYSTIMESTAMP, SYSTIMESTAMP);

        MERGE INTO nb_identity.customer target
        USING (SELECT v_user_id customer_id, v_user_id user_id, v_customer_number customer_number FROM dual) source
        ON (target.customer_id = source.customer_id)
        WHEN MATCHED THEN UPDATE SET
            target.user_id = source.user_id,
            target.customer_number = source.customer_number,
            target.first_name = v_first_name,
            target.last_name = v_last_name,
            target.date_of_birth = DATE '1990-01-01' + (customer_index * 500),
            target.mobile_number = v_mobile,
            target.kyc_status = 'VERIFIED',
            target.kyc_verified_at = SYSTIMESTAMP,
            target.is_active = 'Y'
        WHEN NOT MATCHED THEN INSERT (
            customer_id, user_id, customer_number, first_name, last_name,
            date_of_birth, mobile_number, kyc_status, kyc_verified_at,
            is_active, created_at, updated_at)
        VALUES (
            source.customer_id, source.user_id, source.customer_number,
            v_first_name, v_last_name, DATE '1990-01-01' + (customer_index * 500),
            v_mobile, 'VERIFIED', SYSTIMESTAMP, 'Y', SYSTIMESTAMP, SYSTIMESTAMP);

        MERGE INTO nb_identity.customer_address target
        USING (SELECT v_user_id customer_id, 'RESIDENTIAL' address_type FROM dual) source
        ON (target.customer_id = source.customer_id AND target.address_type = source.address_type)
        WHEN MATCHED THEN UPDATE SET
            target.address_line_1 = v_address,
            target.address_line_2 = NULL,
            target.city = v_city,
            target.state = 'Karnataka',
            target.postal_code = v_postal_code,
            target.country_code = 'IN',
            target.is_primary = 'Y'
        WHEN NOT MATCHED THEN INSERT (
            customer_id, address_type, address_line_1, city, state,
            postal_code, country_code, is_primary, created_at, updated_at)
        VALUES (
            source.customer_id, source.address_type, v_address, v_city,
            'Karnataka', v_postal_code, 'IN', 'Y', SYSTIMESTAMP, SYSTIMESTAMP);

        MERGE INTO nb_identity.user_role target
        USING (
            SELECT v_user_id user_id, role_id
            FROM nb_identity.role
            WHERE role_code = 'CUSTOMER') source
        ON (target.user_id = source.user_id AND target.role_id = source.role_id)
        WHEN NOT MATCHED THEN INSERT (user_id, role_id, assigned_at)
        VALUES (source.user_id, source.role_id, SYSTIMESTAMP);
    END LOOP;
END;
/

-- Force a fresh Microsoft Authenticator enrolment for these local users.
DELETE FROM nb_identity.user_totp WHERE user_id IN (900001, 900003, 900004);

PROMPT === Accounts: exactly two savings and two current accounts per customer ===

DECLARE
    v_customer_id       NUMBER(19);
    v_account_id        NUMBER(19);
    v_branch_id         NUMBER(19);
    v_account_number    VARCHAR2(20);
    v_account_type      VARCHAR2(20);
    v_balance           NUMBER(19,4);
    v_transaction_id    NUMBER(19);
    v_transaction_ref   VARCHAR2(50);
    v_amount            NUMBER(19,4);
    v_balance_after     NUMBER(19,4);
    v_narration         VARCHAR2(500);
BEGIN
    SELECT branch_id INTO v_branch_id
    FROM nb_accounts.branch
    WHERE ifsc_code = 'NETB0000001' AND is_active = 'Y';

    -- Hide older seed-account relationships so the three profiles expose only
    -- the four accounts defined below.
    UPDATE nb_accounts.account_holder
    SET is_active = 'N'
    WHERE customer_id IN (900001, 900003, 900004)
      AND account_id NOT IN (
          921011, 921012, 921013, 921014,
          921021, 921022, 921023, 921024,
          921031, 921032, 921033, 921034);

    FOR customer_index IN 1..3 LOOP
        v_customer_id := CASE customer_index WHEN 1 THEN 900001 WHEN 2 THEN 900003 ELSE 900004 END;

        FOR account_index IN 1..4 LOOP
            v_account_id := 921000 + customer_index * 10 + account_index;
            v_account_number := '71' || LPAD(TO_CHAR(v_customer_id), 6, '0') || LPAD(TO_CHAR(account_index), 4, '0');
            v_account_type := CASE WHEN account_index <= 2 THEN 'SAVINGS' ELSE 'CURRENT' END;
            v_balance := CASE account_index
                WHEN 1 THEN 150000 + customer_index * 5000
                WHEN 2 THEN 90000 + customer_index * 4000
                WHEN 3 THEN 175000 + customer_index * 7000
                ELSE 80000 + customer_index * 3000
            END;

            MERGE INTO nb_accounts.bank_account target
            USING (
                SELECT v_account_id account_id, v_branch_id branch_id,
                       v_account_number account_number, v_account_type account_type,
                       v_balance balance
                FROM dual) source
            ON (target.account_id = source.account_id)
            WHEN MATCHED THEN UPDATE SET
                target.branch_id = source.branch_id,
                target.account_number = source.account_number,
                target.account_type = source.account_type,
                target.currency_code = 'INR',
                target.account_status = 'ACTIVE',
                target.current_balance = source.balance,
                target.available_balance = source.balance,
                target.closed_at = NULL
            WHEN NOT MATCHED THEN INSERT (
                account_id, branch_id, account_number, account_type, currency_code,
                account_status, current_balance, available_balance,
                opened_at, created_at, updated_at)
            VALUES (
                source.account_id, source.branch_id, source.account_number,
                source.account_type, 'INR', 'ACTIVE', source.balance, source.balance,
                SYSTIMESTAMP, SYSTIMESTAMP, SYSTIMESTAMP);

            MERGE INTO nb_accounts.account_holder target
            USING (SELECT v_account_id account_id, v_customer_id customer_id FROM dual) source
            ON (target.account_id = source.account_id AND target.customer_id = source.customer_id)
            WHEN MATCHED THEN UPDATE SET target.holder_type = 'PRIMARY', target.is_active = 'Y'
            WHEN NOT MATCHED THEN INSERT (
                account_id, customer_id, holder_type, is_active, joined_at)
            VALUES (source.account_id, source.customer_id, 'PRIMARY', 'Y', SYSTIMESTAMP);
        END LOOP;

        -- Realistic activity for the primary savings account. The last entry's
        -- balance equals the current balance written above.
        FOR transaction_index IN 1..5 LOOP
            v_account_id := 921000 + customer_index * 10 + 1;
            v_transaction_id := 925000 + customer_index * 100 + transaction_index;
            v_transaction_ref := 'OIB-ACCT-C' || customer_index || '-' || LPAD(TO_CHAR(transaction_index), 2, '0');
            v_amount := CASE transaction_index
                WHEN 1 THEN 65000 WHEN 2 THEN 2850 WHEN 3 THEN 25000
                WHEN 4 THEN 4299 ELSE 2851
            END;
            v_balance_after := CASE transaction_index
                WHEN 1 THEN 185000 + customer_index * 5000
                WHEN 2 THEN 182150 + customer_index * 5000
                WHEN 3 THEN 157150 + customer_index * 5000
                WHEN 4 THEN 152851 + customer_index * 5000
                ELSE 150000 + customer_index * 5000
            END;
            v_narration := CASE transaction_index
                WHEN 1 THEN 'Monthly salary - Acme Technologies'
                WHEN 2 THEN 'BESCOM electricity bill'
                WHEN 3 THEN 'House rent - Suresh Rao'
                WHEN 4 THEN 'Amazon India purchase'
                ELSE 'Indian Oil fuel payment'
            END;

            MERGE INTO nb_accounts.bank_transaction target
            USING (
                SELECT v_transaction_id transaction_id, v_transaction_ref transaction_reference,
                       CASE WHEN transaction_index = 1 THEN NULL ELSE v_account_id END debit_account_id,
                       CASE WHEN transaction_index = 1 THEN v_account_id ELSE NULL END credit_account_id,
                       v_customer_id initiated_by_user_id,
                       CASE WHEN transaction_index = 1 THEN 'DEPOSIT' ELSE 'TRANSFER' END transaction_type,
                       v_amount amount, v_narration narration,
                       SYSTIMESTAMP - NUMTODSINTERVAL(12 - transaction_index, 'DAY') completed_at
                FROM dual) source
            ON (target.transaction_id = source.transaction_id)
            WHEN MATCHED THEN UPDATE SET
                target.transaction_reference = source.transaction_reference,
                target.debit_account_id = source.debit_account_id,
                target.credit_account_id = source.credit_account_id,
                target.initiated_by_user_id = source.initiated_by_user_id,
                target.transaction_type = source.transaction_type,
                target.transaction_status = 'COMPLETED',
                target.amount = source.amount,
                target.currency_code = 'INR',
                target.narration = source.narration,
                target.completed_at = source.completed_at,
                target.failure_reason = NULL
            WHEN NOT MATCHED THEN INSERT (
                transaction_id, transaction_reference, debit_account_id,
                credit_account_id, initiated_by_user_id, transaction_type,
                transaction_status, amount, currency_code, narration,
                initiated_at, completed_at, created_at, updated_at)
            VALUES (
                source.transaction_id, source.transaction_reference,
                source.debit_account_id, source.credit_account_id,
                source.initiated_by_user_id, source.transaction_type,
                'COMPLETED', source.amount, 'INR', source.narration,
                source.completed_at, source.completed_at,
                source.completed_at, source.completed_at);

            MERGE INTO nb_accounts.account_transaction_entry target
            USING (
                SELECT 926000 + customer_index * 100 + transaction_index entry_id,
                       v_transaction_id transaction_id, v_account_id account_id,
                       CASE WHEN transaction_index = 1 THEN 'CREDIT' ELSE 'DEBIT' END entry_type,
                       v_amount amount, v_balance_after balance_after,
                       SYSTIMESTAMP - NUMTODSINTERVAL(12 - transaction_index, 'DAY') posted_at
                FROM dual) source
            ON (target.entry_id = source.entry_id)
            WHEN MATCHED THEN UPDATE SET
                target.transaction_id = source.transaction_id,
                target.account_id = source.account_id,
                target.entry_type = source.entry_type,
                target.amount = source.amount,
                target.balance_after = source.balance_after,
                target.posted_at = source.posted_at
            WHEN NOT MATCHED THEN INSERT (
                entry_id, transaction_id, account_id, entry_type,
                amount, balance_after, posted_at)
            VALUES (
                source.entry_id, source.transaction_id, source.account_id,
                source.entry_type, source.amount, source.balance_after, source.posted_at);
        END LOOP;
    END LOOP;
END;
/

PROMPT === Payments: realistic beneficiaries and billers ===

-- These profiles are disposable development identities, so replace their old
-- placeholder beneficiaries rather than retaining duplicate-looking entries.
DELETE FROM nb_payments.beneficiary
WHERE customer_id IN (900001, 900003, 900004);

DECLARE
    v_customer_id   NUMBER(19);
    v_beneficiary_id NUMBER(19);
    v_nickname      VARCHAR2(100);
    v_name          VARCHAR2(200);
    v_account       VARCHAR2(20);
    v_ifsc          VARCHAR2(11);
    v_bank          VARCHAR2(200);
BEGIN
    FOR customer_index IN 1..3 LOOP
        v_customer_id := CASE customer_index WHEN 1 THEN 900001 WHEN 2 THEN 900003 ELSE 900004 END;
        FOR beneficiary_index IN 1..5 LOOP
            v_beneficiary_id := 941000 + customer_index * 10 + beneficiary_index;
            v_nickname := CASE beneficiary_index
                WHEN 1 THEN 'Mother' WHEN 2 THEN 'House Rent'
                WHEN 3 THEN 'Investments' WHEN 4 THEN 'College Friend'
                ELSE 'Family Savings'
            END;
            v_name := CASE beneficiary_index
                WHEN 1 THEN 'Kavitha Jahagirdar'
                WHEN 2 THEN 'Suresh Rao'
                WHEN 3 THEN 'Groww Invest-Tech Private Limited'
                WHEN 4 THEN 'Arjun Nair'
                ELSE 'Meera Sharma'
            END;
            v_ifsc := CASE beneficiary_index
                WHEN 1 THEN 'HDFC0001234' WHEN 2 THEN 'SBIN0000456'
                WHEN 3 THEN 'KKBK0000456' WHEN 4 THEN 'ICIC0000789'
                ELSE 'UTIB0000123'
            END;
            v_bank := CASE beneficiary_index
                WHEN 1 THEN 'HDFC Bank' WHEN 2 THEN 'State Bank of India'
                WHEN 3 THEN 'Kotak Mahindra Bank' WHEN 4 THEN 'ICICI Bank'
                ELSE 'Axis Bank'
            END;
            v_account := '83' || customer_index || beneficiary_index || LPAD(TO_CHAR(730000 + customer_index * 10 + beneficiary_index), 8, '0');

            INSERT INTO nb_payments.beneficiary (
                beneficiary_id, customer_id, nickname, beneficiary_name,
                account_number, ifsc_code, bank_name, beneficiary_status,
                activated_at, created_at, updated_at)
            VALUES (
                v_beneficiary_id, v_customer_id, v_nickname, v_name,
                v_account, v_ifsc, v_bank, 'ACTIVE',
                SYSTIMESTAMP, SYSTIMESTAMP, SYSTIMESTAMP);
        END LOOP;
    END LOOP;
END;
/

MERGE INTO nb_payments.biller target
USING (
    SELECT 'ELECTRICITY' biller_code, 'BESCOM Electricity' biller_name,
           'UTILITIES' category, 'Consumer number' reference_label,
           '^[0-9]{5,20}$' reference_pattern, 'Enter the BESCOM consumer number.' reference_hint,
           100000 max_amount FROM dual
    UNION ALL SELECT 'MOBILE', 'Airtel Postpaid', 'TELECOM', 'Mobile number',
           '^[0-9]{10}$', 'Enter the registered 10-digit mobile number.', 50000 FROM dual
    UNION ALL SELECT 'WATER', 'Bangalore Water Supply and Sewerage Board', 'UTILITIES',
           'Connection number', '^[A-Za-z0-9][A-Za-z0-9-]{4,29}$',
           'Enter the BWSSB connection number.', 100000 FROM dual
    UNION ALL SELECT 'BROADBAND', 'JioFiber Broadband', 'BROADBAND', 'Customer ID',
           '^[A-Za-z0-9-]{6,20}$', 'Enter the JioFiber customer ID.', 50000 FROM dual
    UNION ALL SELECT 'GAS', 'Bharat Gas', 'UTILITIES', 'Consumer number',
           '^[0-9]{6,20}$', 'Enter the LPG consumer number.', 50000 FROM dual
) source
ON (target.biller_code = source.biller_code)
WHEN MATCHED THEN UPDATE SET
    target.biller_name = source.biller_name,
    target.category = source.category,
    target.reference_label = source.reference_label,
    target.reference_pattern = source.reference_pattern,
    target.reference_hint = source.reference_hint,
    target.min_amount = 1,
    target.max_amount = source.max_amount,
    target.is_active = 'Y'
WHEN NOT MATCHED THEN INSERT (
    biller_code, biller_name, category, reference_label,
    reference_pattern, reference_hint, min_amount, max_amount,
    is_active, created_at)
VALUES (
    source.biller_code, source.biller_name, source.category,
    source.reference_label, source.reference_pattern, source.reference_hint,
    1, source.max_amount, 'Y', SYSTIMESTAMP);

PROMPT === Products: active debit/credit cards and posted card purchases ===

-- Retire older cards for these disposable profiles so the UI shows the two
-- cards created below instead of accumulating cards across seed revisions.
UPDATE nb_products.bank_card
SET card_status = 'CLOSED', updated_at = SYSTIMESTAMP
WHERE customer_id IN (900001, 900003, 900004)
  AND card_id NOT IN (992011, 992012, 992021, 992022, 992031, 992032);

DECLARE
    v_customer_id NUMBER(19);
    v_account_id  NUMBER(19);
    v_card_id     NUMBER(19);
BEGIN
    FOR customer_index IN 1..3 LOOP
        v_customer_id := CASE customer_index WHEN 1 THEN 900001 WHEN 2 THEN 900003 ELSE 900004 END;

        FOR card_index IN 1..2 LOOP
            v_card_id := 992000 + customer_index * 10 + card_index;
            v_account_id := 921000 + customer_index * 10 + CASE card_index WHEN 1 THEN 1 ELSE 3 END;

            MERGE INTO nb_products.bank_card target
            USING (
                SELECT v_card_id card_id, v_customer_id customer_id,
                       v_account_id account_id,
                       'oib-local-c' || customer_index || '-' || card_index card_token,
                       LPAD(TO_CHAR(5000 + customer_index * 10 + card_index), 4, '0') last_four,
                       CASE card_index WHEN 1 THEN 'DEBIT' ELSE 'CREDIT' END card_type,
                       CASE card_index WHEN 1 THEN 'VISA' ELSE 'MASTERCARD' END card_network
                FROM dual) source
            ON (target.card_id = source.card_id)
            WHEN MATCHED THEN UPDATE SET
                target.customer_id = source.customer_id,
                target.account_id = source.account_id,
                target.card_token = source.card_token,
                target.last_four = source.last_four,
                target.card_type = source.card_type,
                target.card_network = source.card_network,
                target.expiry_month = 12,
                target.expiry_year = 2030,
                target.card_status = 'ACTIVE',
                target.activated_at = SYSTIMESTAMP,
                target.credit_limit = CASE WHEN source.card_type = 'CREDIT' THEN 200000 ELSE NULL END,
                target.outstanding_balance = CASE WHEN source.card_type = 'CREDIT' THEN 8448 ELSE NULL END,
                target.statement_balance = CASE WHEN source.card_type = 'CREDIT' THEN 7149 ELSE NULL END,
                target.minimum_due = CASE WHEN source.card_type = 'CREDIT' THEN 715 ELSE NULL END,
                target.statement_date = CASE WHEN source.card_type = 'CREDIT' THEN TRUNC(SYSDATE) - 5 ELSE NULL END,
                target.payment_due_date = CASE WHEN source.card_type = 'CREDIT' THEN TRUNC(SYSDATE) + 15 ELSE NULL END,
                target.updated_at = SYSTIMESTAMP
            WHEN NOT MATCHED THEN INSERT (
                card_id, customer_id, account_id, card_token, last_four,
                card_type, card_network, expiry_month, expiry_year, card_status,
                issued_at, activated_at, updated_at, credit_limit,
                outstanding_balance, statement_balance, minimum_due,
                statement_date, payment_due_date)
            VALUES (
                source.card_id, source.customer_id, source.account_id,
                source.card_token, source.last_four, source.card_type,
                source.card_network, 12, 2030, 'ACTIVE', SYSTIMESTAMP,
                SYSTIMESTAMP, SYSTIMESTAMP,
                CASE WHEN source.card_type = 'CREDIT' THEN 200000 ELSE NULL END,
                CASE WHEN source.card_type = 'CREDIT' THEN 8448 ELSE NULL END,
                CASE WHEN source.card_type = 'CREDIT' THEN 7149 ELSE NULL END,
                CASE WHEN source.card_type = 'CREDIT' THEN 715 ELSE NULL END,
                CASE WHEN source.card_type = 'CREDIT' THEN TRUNC(SYSDATE) - 5 ELSE NULL END,
                CASE WHEN source.card_type = 'CREDIT' THEN TRUNC(SYSDATE) + 15 ELSE NULL END);
        END LOOP;

        v_card_id := 992000 + customer_index * 10 + 2;
        FOR transaction_index IN 1..3 LOOP
            MERGE INTO nb_products.card_transaction target
            USING (
                SELECT 993000 + customer_index * 10 + transaction_index card_transaction_id,
                       v_card_id card_id,
                       'OIB-CARD-' || customer_index || '-' || transaction_index transaction_reference,
                       CASE transaction_index
                           WHEN 1 THEN 'Amazon India'
                           WHEN 2 THEN 'Indian Oil'
                           ELSE 'Taj Hotels'
                       END merchant_name,
                       CASE transaction_index
                           WHEN 1 THEN 'ONLINE_RETAIL'
                           WHEN 2 THEN 'FUEL'
                           ELSE 'HOTELS'
                       END merchant_category,
                       CASE transaction_index WHEN 1 THEN 4299 WHEN 2 THEN 2850 ELSE 1299 END amount,
                       SYSTIMESTAMP - NUMTODSINTERVAL(transaction_index * 4, 'DAY') posted_at
                FROM dual) source
            ON (target.card_transaction_id = source.card_transaction_id)
            WHEN MATCHED THEN UPDATE SET
                target.card_id = source.card_id,
                target.transaction_reference = source.transaction_reference,
                target.merchant_name = source.merchant_name,
                target.merchant_category = source.merchant_category,
                target.transaction_type = 'PURCHASE',
                target.amount = source.amount,
                target.currency_code = 'INR',
                target.transaction_status = 'POSTED',
                target.posted_at = source.posted_at,
                target.billed_on = TRUNC(SYSDATE) - 5
            WHEN NOT MATCHED THEN INSERT (
                card_transaction_id, card_id, transaction_reference,
                merchant_name, merchant_category, transaction_type,
                amount, currency_code, transaction_status, posted_at, billed_on)
            VALUES (
                source.card_transaction_id, source.card_id,
                source.transaction_reference, source.merchant_name,
                source.merchant_category, 'PURCHASE', source.amount,
                'INR', 'POSTED', source.posted_at, TRUNC(SYSDATE) - 5);
        END LOOP;
    END LOOP;
END;
/

COMMIT;

PROMPT === Verification ===

SELECT user_id, username, email, account_status
FROM nb_identity.app_user
WHERE user_id IN (900001, 900003, 900004)
ORDER BY user_id;

SELECT holder.customer_id, account.account_type, COUNT(*) account_count,
       MIN(account.available_balance) minimum_balance
FROM nb_accounts.account_holder holder
JOIN nb_accounts.bank_account account ON account.account_id = holder.account_id
WHERE holder.customer_id IN (900001, 900003, 900004)
  AND holder.is_active = 'Y'
  AND account.account_status = 'ACTIVE'
GROUP BY holder.customer_id, account.account_type
ORDER BY holder.customer_id, account.account_type;

SELECT customer_id, nickname, beneficiary_name, bank_name, beneficiary_status
FROM nb_payments.beneficiary
WHERE customer_id IN (900001, 900003, 900004)
ORDER BY customer_id, beneficiary_id;

SELECT biller_code, biller_name, category
FROM nb_payments.biller
WHERE biller_code IN ('ELECTRICITY', 'MOBILE', 'WATER', 'BROADBAND', 'GAS')
ORDER BY biller_code;

SELECT card.customer_id, card.card_type, card.card_network, card.last_four,
       card.card_status, COUNT(transaction.card_transaction_id) transaction_count
FROM nb_products.bank_card card
LEFT JOIN nb_products.card_transaction transaction ON transaction.card_id = card.card_id
WHERE card.card_id IN (992011, 992012, 992021, 992022, 992031, 992032)
GROUP BY card.customer_id, card.card_type, card.card_network,
         card.last_four, card.card_status
ORDER BY card.customer_id, card.card_type;

PROMPT === Seed completed successfully ===
