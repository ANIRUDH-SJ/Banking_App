import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { buildFreshJars } from './local-build.mjs';
import { serviceLaunch, serviceNames } from './service-launch.mjs';

const name = process.argv[2];
if (!serviceNames.includes(name)) throw new Error(`Choose one of: ${serviceNames.join(', ')}`);
const root = fileURLToPath(new URL('../', import.meta.url));
// The all-services launcher builds once and marks its children as prebuilt.
// A direct single-service launch must not silently reuse a stale JAR.
if (process.env.BANKING_JARS_BUILT !== '1') {
  console.log('Building fresh service JARs from this checkout first...');
  await buildFreshJars(root);
}
const launch = serviceLaunch(root, name, { eventTransport: process.env.BANKING_EVENT_TRANSPORT });
const child = spawn(launch.command, launch.args, { ...launch.options, stdio: 'inherit' });
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => child.kill(signal));
child.on('error', error => { console.error(`Could not start Java: ${error.message}`); process.exitCode = 1; });
child.on('exit', code => { process.exitCode = code ?? 1; });
