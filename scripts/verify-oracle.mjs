import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
const root = fileURLToPath(new URL('../', import.meta.url));
const services = ['identity-service', 'accounts-ledger-service', 'payments-service', 'products-service', 'notification-service', 'audit-reporting-service'];
for (const name of services) {
  const config = JSON.parse(readFileSync(join(root, '.local', `${name}.json`), 'utf8'));
  if (!config.DB_USERNAME?.startsWith('NB_') || config.DB_PASSWORD?.startsWith('REPLACE_')) {
    throw new Error(`Configure the private local schema for ${name} first.`);
  }
  console.log(`Migrating and validating ${name} against its local Oracle schema...`);
  const windows = process.platform === 'win32';
  const args = ['-pl', `:${name}`, '-am', '-Dtest=OracleSchemaValidationTest', '-Dsurefire.failIfNoSpecifiedTests=false', 'test'];
  const command = windows ? 'cmd.exe' : 'bash';
  const result = spawnSync(command, windows ? ['/d', '/s', '/c', 'mvnw.cmd', ...args] : ['./mvnw', ...args], {
    cwd: root, stdio: 'inherit', env: { ...process.env, ...config, ORACLE_SCHEMA_VALIDATION: 'true' },
  });
  if (result.error || result.status !== 0) process.exit(result.status || 1);
}
