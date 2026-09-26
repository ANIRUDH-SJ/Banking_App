-- Enforce the same case-insensitive identity rules used by authentication.
CREATE UNIQUE INDEX uq_app_user_username_ci ON app_user (LOWER(username));
CREATE UNIQUE INDEX uq_app_user_email_ci ON app_user (LOWER(email));
