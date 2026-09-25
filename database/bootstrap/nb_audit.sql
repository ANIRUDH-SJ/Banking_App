-- Connect as a PDB administrator to FREEPDB1. Run once in SQLcl/SQL*Plus.
-- Use a fresh local-only password without double quotes or ampersands.
SET ECHO OFF
SET VERIFY OFF
WHENEVER SQLERROR EXIT SQL.SQLCODE
ACCEPT schema_password CHAR PROMPT 'Password for NB_AUDIT: ' HIDE
CREATE USER NB_AUDIT IDENTIFIED BY "&schema_password" DEFAULT TABLESPACE USERS QUOTA 250M ON USERS;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE TRIGGER TO NB_AUDIT;
UNDEFINE schema_password
