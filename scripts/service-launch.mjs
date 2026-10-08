import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { loadServiceConfig } from './local-env.mjs';

const modules = {
  'service-registry': 'platform',
  'api-gateway': 'platform',
  'identity-service': 'services',
  'accounts-ledger-service': 'services',
  'payments-service': 'services',
  'products-service': 'services',
  'notification-service': 'services',
  'audit-reporting-service': 'services',
};

// Local runs have no SMS provider by default. A configured provider overrides this.
const localDefaults = {
  'notification-service': { NOTIFICATION_LOG_MESSAGE_CONTENT: 'true' },
};

export const serviceNames = Object.keys(modules);

export function serviceLaunch(root, name, { env = process.env, eventTransport } = {}) {
  if (!Object.hasOwn(modules, name)) {
    throw new Error(`Choose one of: ${serviceNames.join(', ')}`);
  }
  const config = loadServiceConfig(root, name);
  if (eventTransport) config.EVENT_TRANSPORT = eventTransport;
  for (const [key, value] of Object.entries(config)) {
    if (typeof value !== 'string' || value.startsWith('REPLACE_')) {
      throw new Error(`Configure ${key} in .local/${name}.json or .env`);
    }
  }
  const jar = join(root, modules[name], name, 'target', `${name}-0.0.1-SNAPSHOT.jar`);
  if (!existsSync(jar)) {
    throw new Error(`Missing ${name} JAR. Build service JARs with ./mvnw clean package -DskipTests first.`);
  }
  const javaHome = env.JAVA_HOME?.replace(/^["']|["']$/g, '');
  const java = javaHome
    ? join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
    : 'java';
  return {
    command: java,
    args: ['-Xms64m', '-Xmx256m', '-jar', jar],
    options: { cwd: root, env: { ...localDefaults[name], ...env, ...config } },
  };
}
