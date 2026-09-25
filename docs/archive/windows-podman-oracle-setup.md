> Historical monolith documentation. For the current service schemas and commands, use [teammate setup](../TEAMMATE_QUICK_START.md). Do not run the old schema instructions on the new service schemas.

# Windows + Podman + Oracle Database: teammate setup

Use this guide once on **each developer's own computer**. Each developer runs a personal Oracle database container; nobody shares a database password or connects to another developer's laptop.

## 1. What you need before starting

Install these programs on Windows:

- Git
- Podman Desktop
- Oracle SQL Developer

You also need an Oracle account with permission to download the Oracle Database Free container image. If Oracle Container Registry asks you to accept its terms for the `database/free` image, accept them in the browser before continuing.

Open **PowerShell** after installing Podman Desktop.

## 2. Get the project files

When the team GitHub repository exists, clone it. Replace the placeholder with the real repository address:

```powershell
git clone <repository-url>
cd internet-net-banking
```

The file used later in this guide is:

```text
database\00_schema_setup.sql
```

## 3. Start Podman

Open Podman Desktop and wait until it says **Running**. Then run:

```powershell
podman --version
```

If this is the first time the developer has used Podman, run this next only when `podman machine list` shows no machine:

```powershell
podman machine init --cpus 4 --memory 4096 --disk-size 100
```

Then start the machine:

```powershell
podman machine start
```

If PowerShell says the machine is already running, that is fine. The Oracle container can run with less memory for a small demonstration, but 4 GB is the recommended team-development setting.

## 4. Sign in to Oracle Container Registry and download Oracle Database Free

Run:

```powershell
podman login container-registry.oracle.com
```

Enter your own Oracle account credentials or Oracle authentication token when prompted. Do not share them.

Then download the database image:

```powershell
podman pull container-registry.oracle.com/database/free:latest
```

The download is large and may take several minutes.

## 5. Create persistent database storage

Run this once:

```powershell
podman volume create netbanking-oradata
```

This volume is where Oracle stores its data files. Stopping the container does not remove this volume.

If you see `volume already exists`, continue. It means the volume was created earlier.

## 6. Choose the Oracle administrator password safely

The Oracle image uses this password for its administrative accounts, including `SYS`. Each developer chooses a different private password.

Run this PowerShell block. It prompts for the password without putting it into command history, then stores it as a local Podman secret:

```powershell
$oracleSecret = Read-Host 'Choose a private Oracle administrator password' -AsSecureString
$oracleSecretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($oracleSecret)
try {
    [Runtime.InteropServices.Marshal]::PtrToStringBSTR($oracleSecretPointer) | podman secret create oracle_pwd -
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($oracleSecretPointer)
}
Remove-Variable oracleSecret, oracleSecretPointer
```

Choose at least 12 characters with uppercase, lowercase, a number, and a special character. Do not use a name, phone number, or a password that was shared in chat or screenshots.

If Podman says `secret oracle_pwd already exists` on a machine that was set up before, do not create a second secret. Use the original administrator password for that existing local database. If it is forgotten, ask the project lead before resetting it.

## 7. Start the Oracle container

Run this once on a fresh machine:

```powershell
podman run -d --name netbanking-oracle --restart=unless-stopped -p 1521:1521 --secret oracle_pwd -v netbanking-oradata:/opt/oracle/oradata container-registry.oracle.com/database/free:latest
```

Meaning:

- `--name netbanking-oracle` gives the container a predictable name.
- `-p 1521:1521` exposes Oracle's listener port locally.
- `--secret oracle_pwd` gives the container the private administrator password.
- `-v netbanking-oradata:/opt/oracle/oradata` preserves the database files.

If PowerShell says `name netbanking-oracle is already in use`, do not run this command again. Use the daily-start command in section 13.

## 8. Wait for Oracle to finish starting

Run:

```powershell
podman logs -f netbanking-oracle
```

Wait until this exact message appears:

```text
DATABASE IS READY TO USE!
```

Press `Ctrl + C` to stop viewing logs. This does **not** stop the database.

Confirm the health status:

```powershell
podman ps --filter name=netbanking-oracle
```

Continue only when the status includes `healthy`.

## 9. Check the local Oracle port

Run:

```powershell
Test-NetConnection 127.0.0.1 -Port 1521
```

If `TcpTestSucceeded : True`, proceed to section 11.

If it is `False`, use the SSH tunnel workaround in section 10. This is sometimes needed with Podman on Windows/WSL.

## 10. SSH tunnel workaround for Podman on Windows/WSL

This tunnel is local to the developer's own computer. It allows SQL Developer to reach the Oracle port even if Podman's normal port forwarding fails.

First inspect the local Podman machine:

```powershell
podman machine inspect
```

Find these three values under `SSHConfig`:

- `IdentityPath`
- `Port`
- `RemoteUsername`

Open a **new PowerShell window**. Replace the three placeholders with the values from `podman machine inspect`:

```powershell
ssh -i "<IdentityPath>" -p <Port> -N -L 127.0.0.1:1521:127.0.0.1:1521 <RemoteUsername>@127.0.0.1
```

The first time, SSH may ask whether to trust the key. Type `yes`.

The window normally stays blank while the tunnel works. Leave it open. In the original PowerShell window, test again:

```powershell
Test-NetConnection 127.0.0.1 -Port 1521
```

Continue only when it shows:

```text
TcpTestSucceeded : True
```

## 11. Create the SQL Developer SYS administration connection

1. Open Oracle SQL Developer.
2. Click the green **+** next to Connections.
3. Enter these values:

```text
Name: netbanking-local-admin
Username: sys
Password: the private Oracle administrator password chosen in section 6
Role: SYSDBA
Connection Type: Basic
Hostname: 127.0.0.1
Port: 1521
Select: Service name
Service name: FREEPDB1
```

4. Click **Test**. It must show `Status: Success`.
5. Click **Connect**.
6. Open a SQL Worksheet and run:

```sql
SELECT
    SYS_CONTEXT('USERENV', 'CON_NAME') AS container_name,
    USER AS connected_user
FROM dual;
```

Expected result:

```text
FREEPDB1   SYS
```

`SYS` is an administrator account. It is only used to create and manage the application schema. It is never used by Spring Boot.

## 12. Create the application schema from the project script

1. In SQL Developer, use the connected `netbanking-local-admin` connection.
2. Select **File > Open**.
3. Open `database/00_schema_setup.sql` from the cloned project.
4. Press **F5** (Run Script), not Ctrl + Enter.
5. When prompted, choose a new, private password for `NET_BANKING_APP`.
   - This password is different from the SYS administrator password.
   - Do not share or commit it.
6. Wait for this message:

```text
NET_BANKING_APP schema setup completed successfully.
```

The script creates `NET_BANKING_APP` and grants its table-creation permissions. It does not create banking tables yet.

## 13. Create the SQL Developer application connection

1. Click the green **+** next to Connections.
2. Enter:

```text
Name: netbanking-app-local
Username: net_banking_app
Password: the private NET_BANKING_APP password from section 12
Role: Default
Connection Type: Basic
Hostname: 127.0.0.1
Port: 1521
Select: Service name
Service name: FREEPDB1
```

3. Click **Test** and confirm `Status: Success`.
4. Click **Connect**.
5. Open its SQL Worksheet and run:

```sql
SELECT USER AS connected_user
FROM dual;
```

Expected result:

```text
NET_BANKING_APP
```

## 14. Daily startup and shutdown

### Start work

```powershell
podman machine start
podman start netbanking-oracle
podman ps --filter name=netbanking-oracle
```

Wait for `healthy`. Test port `1521`. If it fails, start the SSH tunnel again using section 10.

### Finish work

1. Close SQL Developer connections.
2. If an SSH tunnel is running, select its PowerShell window and press `Ctrl + C`.
3. Stop Oracle:

```powershell
podman stop netbanking-oracle
```

4. Optionally stop the Podman machine:

```powershell
podman machine stop
```

Do not delete `netbanking-oradata`. It contains the local database data.

## 15. Common problems

### `ORA-12541: no listener`

Check in this order:

```powershell
podman ps --filter name=netbanking-oracle
Test-NetConnection 127.0.0.1 -Port 1521
```

The container must be `healthy` and the port test must be `True`. If the port test is false, use section 10.

### `ORA-01017: invalid username/password`

Check the connection type:

- `SYS` connection: username `sys`, role `SYSDBA`, Oracle administrator password.
- Application connection: username `net_banking_app`, role `Default`, application-schema password.

Do not put the application password into the SYS connection.

### `ORA-01917: user or role does not exist`

The schema setup did not complete. Reconnect as `SYSDBA` and run `database/00_schema_setup.sql` with F5.

### Container is healthy but the port test is false

Use the SSH tunnel workaround in section 10. Keep the tunnel window open while SQL Developer and future Spring Boot use Oracle.

## 16. Team completion evidence

Each teammate must provide these results before features are assigned:

- `podman ps --filter name=netbanking-oracle` shows `healthy`.
- `Test-NetConnection 127.0.0.1 -Port 1521` shows `TcpTestSucceeded : True`.
- SQL Developer `netbanking-local-admin` query returns `FREEPDB1` and `SYS`.
- SQL Developer `netbanking-app-local` query returns `NET_BANKING_APP`.
