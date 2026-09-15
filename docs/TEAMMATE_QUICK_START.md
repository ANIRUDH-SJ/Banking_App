# Internet Net Banking — teammate quick start

Each developer runs their **own local Oracle database**. Do not share passwords, Podman volumes, or SSH keys.

## One-time setup

1. Extract the project folder into your Documents folder.

   ```text
   C:\Users\<your-name>\Documents\internet-net-banking
   ```

2. Install:
   - Podman Desktop
   - Oracle SQL Developer
   - Git

3. Open Podman Desktop and wait until it shows **Running**.

4. Open PowerShell and run:

   ```powershell
   podman machine start
   ```

5. Follow the full setup guide in this project:

   ```text
   docs\windows-podman-oracle-setup.md
   ```

   It tells you how to download Oracle, create your personal database password, and start the `netbanking-oracle` container.

6. Wait until this command shows `healthy`:

   ```powershell
   podman ps --filter name=netbanking-oracle
   ```

7. Test the Oracle port:

   ```powershell
   Test-NetConnection 127.0.0.1 -Port 1521
   ```

   If the result is `False`, follow the SSH tunnel section in `docs\windows-podman-oracle-setup.md`. Continue only when it is `True`.

8. In SQL Developer, create this administrator connection:

   ```text
   Name: netbanking-local-admin
   Username: sys
   Role: SYSDBA
   Hostname: 127.0.0.1
   Port: 1521
   Service name: FREEPDB1
   Password: your own Oracle administrator password
   ```

9. Using `netbanking-local-admin`, open and run **with F5**:

   ```text
   database\00_schema_setup.sql
   ```

   Choose a private password for `NET_BANKING_APP` when asked.

10. In SQL Developer, create this application connection:

    ```text
    Name: netbanking-app-local
    Username: net_banking_app
    Role: Default
    Hostname: 127.0.0.1
    Port: 1521
    Service name: FREEPDB1
    Password: your own NET_BANKING_APP password
    ```

11. Using `netbanking-app-local`, open and run **with F5**:

    ```text
    database\01_security_tables.sql
    ```

12. Verify the initial role data:

    ```sql
    SELECT role_code, role_name
    FROM role
    ORDER BY role_id;
    ```

    Expected result:

    ```text
    ADMIN
    CUSTOMER
    ```

## Daily startup

1. Open Podman Desktop.
2. Run:

   ```powershell
   podman machine start
   podman start netbanking-oracle
   ```

3. Wait until `podman ps --filter name=netbanking-oracle` shows `healthy`.
4. Run `Test-NetConnection 127.0.0.1 -Port 1521`.
5. If the port test is false, start the SSH tunnel using the full guide.
6. Open SQL Developer and connect using `netbanking-app-local`.

## Never share

- Oracle passwords
- `NET_BANKING_APP` passwords
- Podman volumes
- SSH keys
- SQL Developer saved-password files
