-- Development seed repair for seed_customer_01 only. Run as NB_PRODUCTS in SQL Developer.
-- The legacy development seed labels card 0103 as CREDIT but omits its billing account.
-- Do not run this as a production migration or for a real customer card.
BEGIN
    UPDATE bank_card
    SET credit_limit = 150000.00,
        outstanding_balance = 0.00,
        statement_balance = 0.00,
        minimum_due = 0.00,
        statement_date = TRUNC(SYSDATE),
        payment_due_date = TRUNC(SYSDATE) + 20
    WHERE card_id = 990013
      AND customer_id = 900001
      AND card_token = 'seed-card-1-3'
      AND last_four = '0103'
      AND card_type = 'CREDIT'
      AND credit_limit IS NULL;

    IF SQL%ROWCOUNT <> 1 THEN
        RAISE_APPLICATION_ERROR(-20001, 'Expected exactly one unconfigured seed card 0103.');
    END IF;
    COMMIT;
END;
/

SELECT card_id, card_type, credit_limit, outstanding_balance, statement_balance,
       minimum_due, statement_date, payment_due_date
FROM bank_card
WHERE card_id = 990013 AND card_token = 'seed-card-1-3';
