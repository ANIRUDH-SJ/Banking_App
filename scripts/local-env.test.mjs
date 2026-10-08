import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { parseEnv } from 'node:util';
import { loadServiceConfig, serializeEnv, updateServiceEnv } from './local-env.mjs';

test('service credentials remain isolated and scoped env overrides legacy JSON', () => {
  const root = mkdtempSync(join(tmpdir(), 'banking-env-'));
  try {
    mkdirSync(join(root, '.local'));
    writeFileSync(join(root, '.local', 'payments-service.json'), JSON.stringify({ DB_USERNAME: 'NB_PAYMENTS', SERVICE_TOKEN: 'old' }));
    writeFileSync(join(root, '.env'), serializeEnv({ EVENT_TRANSPORT: 'http', PAYMENTS_SERVICE__SERVICE_TOKEN: 'payment-token', IDENTITY_SERVICE__JWT_PRIVATE_KEY: 'identity-only', NOTIFICATION_SERVICE__SMTP_PASSWORD: 'smtp-only' }));
    const result = loadServiceConfig(root, 'payments-service');
    assert.deepEqual(result, { DB_USERNAME: 'NB_PAYMENTS', SERVICE_TOKEN: 'payment-token', EVENT_TRANSPORT: 'http' });
    updateServiceEnv(root, 'notification-service', { SMTP_PASSWORD: 'new#secret' });
    assert.equal(loadServiceConfig(root, 'notification-service').SMTP_PASSWORD, 'new#secret');
    assert.equal(loadServiceConfig(root, 'identity-service').JWT_PRIVATE_KEY, 'identity-only');
  } finally { rmSync(root, { recursive: true }); }
});

test('dotenv export preserves hash, equals, spaces, dollar signs and backslashes', () => {
  const values = { SECRET: 'a#b=c $d\\e', URL: 'http://user:password@localhost:8761/eureka/' };
  assert.deepEqual(parseEnv(serializeEnv(values)), values);
});
