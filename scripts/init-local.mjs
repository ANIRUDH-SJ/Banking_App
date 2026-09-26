import { generateKeyPairSync, randomBytes, createHash } from 'node:crypto';
import { mkdirSync, existsSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const root = fileURLToPath(new URL('../', import.meta.url));
const directory = join(root, '.local');
if (existsSync(directory)) {
  throw new Error('.local already exists. Edit its files to preserve keys and enrolled authenticators.');
}
const applications = {
  'identity-service': [8081, 'IDENTITY'],
  'accounts-ledger-service': [8082, 'ACCOUNTS'],
  'payments-service': [8083, 'PAYMENTS'],
  'products-service': [8084, 'PRODUCTS'],
  'notification-service': [8085, 'NOTIFICATION'],
  'audit-reporting-service': [8086, 'AUDIT'],
};
const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 3072 });
const tokens = Object.fromEntries(Object.keys(applications).map(name => [name, randomBytes(32).toString('hex')]));
const hashes = Object.fromEntries(Object.entries(applications).map(([name, [, prefix]]) => [
  `${prefix}_SERVICE_TOKEN_HASH`, createHash('sha256').update(tokens[name]).digest('hex'),
]));
const registryUser = 'banking-local';
const registryPassword = randomBytes(24).toString('hex');
const common = {
  JWT_PUBLIC_KEY: publicKey.export({ type: 'spki', format: 'der' }).toString('base64'),
  EUREKA_URL: `http://${registryUser}:${registryPassword}@localhost:8761/eureka/`,
  SERVICE_HOST: 'localhost',
  SERVER_ADDRESS: '127.0.0.1',
  ...hashes,
};
mkdirSync(directory, { mode: 0o700 });
function save(name, config) {
  writeFileSync(join(directory, `${name}.json`), JSON.stringify(config, null, 2) + '\n', { flag: 'wx', mode: 0o600 });
}
for (const [name, [port]] of Object.entries(applications)) {
  save(name, {
    ...common,
    SERVICE_TOKEN: tokens[name],
    SERVER_PORT: String(port),
    ...(name === 'identity-service' ? {
      JWT_PRIVATE_KEY: privateKey.export({ type: 'pkcs8', format: 'der' }).toString('base64'),
      TOTP_ENCRYPTION_KEY: randomBytes(32).toString('base64'),
      SPRING_PROFILES_ACTIVE: 'local',
    } : {}),
  });
}
save('api-gateway', { ...common, SERVICE_TOKEN: randomBytes(32).toString('hex'), SERVER_PORT: '8080' });
save('service-registry', { EUREKA_USERNAME: registryUser, EUREKA_PASSWORD: registryPassword, SERVER_ADDRESS: '127.0.0.1' });
console.log('Created private service configuration files in .local.');
console.log('Keep .local private and backed up. Never commit or share it.');
