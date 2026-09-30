MERGE INTO bank target
USING (
    SELECT
        'NETB' AS bank_code,
        'Net Banking Limited' AS legal_name,
        'Net Banking' AS display_name
    FROM dual
) source
ON (target.bank_code = source.bank_code)
WHEN NOT MATCHED THEN
    INSERT (bank_code, legal_name, display_name, is_active)
    VALUES (source.bank_code, source.legal_name, source.display_name, 'Y');

MERGE INTO branch target
USING (
    SELECT bank_id
    FROM bank
    WHERE bank_code = 'NETB'
) source
ON (target.ifsc_code = 'NETB0000001')
WHEN NOT MATCHED THEN
    INSERT (
        bank_id,
        branch_code,
        branch_name,
        ifsc_code,
        email,
        phone_number,
        address_line_1,
        city,
        state,
        postal_code,
        is_active
    )
    VALUES (
        source.bank_id,
        'DIGITAL',
        'Digital Banking Branch',
        'NETB0000001',
        'support@netbanking.example',
        '+910000000000',
        '1 Digital Banking Way',
        'Bengaluru',
        'Karnataka',
        '560001',
        'Y'
    );
