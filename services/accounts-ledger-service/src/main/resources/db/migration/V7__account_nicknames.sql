-- A nickname belongs to one holder's view of the account, so joint holders keep their own labels.
ALTER TABLE account_holder ADD (nickname VARCHAR2(40 CHAR));
