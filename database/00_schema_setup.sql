-- Internet Net Banking Application
-- Local Oracle schema setup
--
-- Run this file as SYS with the SYSDBA role while connected to FREEPDB1.
-- Run it as a script (F5) in Oracle SQL Developer.
--
-- Security rule: never place a real password in this file or commit one to Git.
-- When prompted, enter a unique local-development password. Do not use a double quote (")
-- in that password because Oracle uses double quotes to preserve its exact characters.

ACCEPT app_password CHAR PROMPT 'Enter a private password for NET_BANKING_APP: ' HIDE

-- Stop the script if Oracle reports an error, for example when the user already exists.
WHENEVER SQLERROR EXIT SQL.SQLCODE

DECLARE
    v_user_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_user_count
      FROM dba_users
     WHERE username = 'NET_BANKING_APP';

    IF v_user_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20001,
            'NET_BANKING_APP already exists. Do not rerun this setup script. Use ALTER USER to reset its password if needed.'
        );
    END IF;
END;
/

CREATE USER net_banking_app
IDENTIFIED BY "&app_password"
DEFAULT TABLESPACE users
TEMPORARY TABLESPACE temp
QUOTA 500M ON users;

GRANT
    CREATE SESSION,
    CREATE TABLE,
    CREATE SEQUENCE,
    CREATE PROCEDURE,
    CREATE TRIGGER,
    CREATE VIEW
TO net_banking_app;

PROMPT NET_BANKING_APP schema setup completed successfully.

UNDEFINE app_password
WHENEVER SQLERROR CONTINUE NONE
