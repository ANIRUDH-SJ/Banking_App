import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { serializeEnv } from './local-env.mjs';
import { serviceLaunch } from './service-launch.mjs';

test('all-services can launch Java directly with only its own credentials', () => {
  const root = mkdtempSync(join(tmpdir(), 'banking-launch-'));
  try {
    mkdirSync(join(root, '.local'));
    mkdirSync(join(root, 'platform', 'api-gateway', 'target'), { recursive: true });
    writeFileSync(join(root, 'platform', 'api-gateway', 'target', 'api-gateway-0.0.1-SNAPSHOT.jar'), '');
    writeFileSync(join(root, '.local', 'api-gateway.json'), JSON.stringify({ SERVICE_TOKEN: 'old-token' }));
    writeFileSync(join(root, '.env'), serializeEnv({
      EVENT_TRANSPORT: 'http',
      API_GATEWAY__SERVICE_TOKEN: 'gateway-token',
      IDENTITY_SERVICE__JWT_PRIVATE_KEY: 'identity-secret',
    }));

    const launch = serviceLaunch(root, 'api-gateway', {
      env: { JAVA_HOME: '"C:\\Program Files\\Java\\jdk-17"' },
      eventTransport: 'kafka',
    });
    assert.equal(launch.options.env.SERVICE_TOKEN, 'gateway-token');
    assert.equal(launch.options.env.EVENT_TRANSPORT, 'kafka');
    assert.equal(launch.options.env.JWT_PRIVATE_KEY, undefined);
    assert.equal(launch.options.env.IDENTITY_SERVICE__JWT_PRIVATE_KEY, undefined);
    assert.equal(launch.command, join('C:\\Program Files\\Java\\jdk-17', 'bin', process.platform === 'win32' ? 'java.exe' : 'java'));
    assert.equal(launch.args.at(-1), join(root, 'platform', 'api-gateway', 'target', 'api-gateway-0.0.1-SNAPSHOT.jar'));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
