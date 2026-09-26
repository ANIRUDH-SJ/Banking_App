import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import { spawn } from 'node:child_process';

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
const name = process.argv[2];
if (!Object.hasOwn(modules, name)) throw new Error(`Choose one of: ${Object.keys(modules).join(', ')}`);
const root = fileURLToPath(new URL('../', import.meta.url));
const config = JSON.parse(readFileSync(join(root, '.local', `${name}.json`), 'utf8'));
for (const [key, value] of Object.entries(config)) {
  if (typeof value !== 'string' || value.startsWith('REPLACE_')) throw new Error(`Configure ${key} in .local/${name}.json`);
}
const jar = join(root, modules[name], name, 'target', `${name}-0.0.1-SNAPSHOT.jar`);
if (!existsSync(jar)) throw new Error('Build the repository with ./mvnw verify first.');
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
const child = spawn(java, ['-Xms64m', '-Xmx256m', '-jar', jar], {
  cwd: root, env: { ...process.env, ...config }, stdio: 'inherit',
});
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => child.kill(signal));
child.on('error', error => { console.error(`Could not start Java: ${error.message}`); process.exitCode = 1; });
child.on('exit', code => { process.exitCode = code ?? 1; });
