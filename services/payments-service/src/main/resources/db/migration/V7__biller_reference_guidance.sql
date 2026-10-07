ALTER TABLE biller ADD (reference_hint VARCHAR2(150 CHAR));

UPDATE biller
SET reference_pattern = '^[0-9]{5,20}$',
    reference_hint = 'Enter 5 to 20 digits.'
WHERE biller_code = 'ELECTRICITY';

UPDATE biller
SET reference_hint = 'Enter a 10-digit mobile number.'
WHERE biller_code = 'MOBILE';

UPDATE biller
SET reference_hint = 'Enter 5 to 30 letters, digits or hyphens.'
WHERE biller_code = 'WATER';
