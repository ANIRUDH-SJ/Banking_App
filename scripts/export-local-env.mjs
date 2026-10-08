import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { generateKeyPairSync, randomBytes } from 'node:crypto';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { servicePrefix, serializeEnv } from './local-env.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
if (existsSync(join(root, '.env'))) throw new Error('.env already exists. Edit it to preserve your keys.');
const values = { EVENT_TRANSPORT: 'http', CORS_ORIGINS: 'http://localhost:8000' };
for (const name of ['service-registry', 'api-gateway', 'identity-service', 'accounts-ledger-service',
  'payments-service', 'products-service', 'notification-service', 'audit-reporting-service']) {
  const config = JSON.parse(readFileSync(join(root, '.local', `${name}.json`), 'utf8'));
  for (const [key, value] of Object.entries(config)) {
    if (typeof value !== 'string' || value.startsWith('REPLACE_')) throw new Error(`Configure ${name}: ${key} first.`);
    values[servicePrefix(name) + key] = value;
  }
}
// Older local setups lack these optional card keys. Preserve any existing keys;
// create stable local keys only where no value was configured.
values.PRODUCTS_SERVICE__CARD_PAN_ENCRYPTION_KEY ||= randomBytes(32).toString('base64');
values.PRODUCTS_SERVICE__CARD_PIN_TRANSPORT_KEY ||= generateKeyPairSync('rsa', { modulusLength: 2048 })
  .privateKey.export({ type: 'pkcs8', format: 'der' }).toString('base64');
values.PAYMENTS_SERVICE__TRANSFER_MAX_AMOUNT ||= '100000';
values.NOTIFICATION_SERVICE__NOTIFICATION_SMS_PROVIDER ||= 'log';
writeFileSync(join(root, '.env'), '# Private local configuration. Node 20.12+ required.\n'
  + '# Service prefixes are removed by run-service.mjs; do not load this file into the frontend.\n'
  + '# These values override .local JSON. Existing JWT/TOTP/SMTP credentials are preserved.\n'
  + serializeEnv(values), { flag: 'wx', mode: 0o600 });
console.log('Created .env from the existing local credentials. No secret values were printed.');
