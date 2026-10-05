import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
const root = fileURLToPath(new URL('../', import.meta.url));
const services = {
  'identity-service': 'NB_IDENTITY',
  'accounts-ledger-service': 'NB_ACCOUNTS',
  'payments-service': 'NB_PAYMENTS',
  'products-service': 'NB_PRODUCTS',
  'notification-service': 'NB_NOTIFICATIONS',
  'audit-reporting-service': 'NB_AUDIT',
};
const configs = Object.entries(services).map(([name, expectedSchema]) => {
  const config = JSON.parse(readFileSync(join(root, '.local', `${name}.json`), 'utf8'));
  if (config.DB_USERNAME !== expectedSchema ||
      typeof config.DB_PASSWORD !== 'string' ||
      !config.DB_PASSWORD.trim() ||
      config.DB_PASSWORD.startsWith('REPLACE_') ||
      (config.MIGRATION_USERNAME && config.MIGRATION_USERNAME !== expectedSchema)) {
    throw new Error(`Configure ${name} with its private ${expectedSchema} schema before running migrations.`);
  }
  return [name, config];
});
for (const [name, config] of configs) {
  console.log(`Migrating and validating ${name} against its local Oracle schema...`);
  const windows = process.platform === 'win32';
  const args = ['-pl', `:${name}`, '-am', '-Dtest=OracleSchemaValidationTest', '-Dsurefire.failIfNoSpecifiedTests=false', 'test'];
  const command = windows ? 'cmd.exe' : 'bash';
  const result = spawnSync(command, windows ? ['/d', '/s', '/c', 'mvnw.cmd', ...args] : ['./mvnw', ...args], {
    cwd: root, stdio: 'inherit', env: { ...process.env, ...config, ORACLE_SCHEMA_VALIDATION: 'true' },
  });
  if (result.error || result.status !== 0) process.exit(result.status || 1);
}
