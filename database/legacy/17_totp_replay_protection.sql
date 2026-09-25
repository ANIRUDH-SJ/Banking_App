-- Prevent an authenticator code from being accepted more than once.
ALTER TABLE user_totp ADD (last_used_time_step NUMBER(19));
