import { spawn } from 'node:child_process';
import { createConnection } from 'node:net';
import { fileURLToPath } from 'node:url';
import { buildFreshJars } from './local-build.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const node = process.execPath;
const runner = fileURLToPath(new URL('./run-service.mjs', import.meta.url));
const children = [];
let stopping = false;
const kafkaEnabled = process.argv.includes('--kafka') || process.env.EVENT_TRANSPORT === 'kafka';

function startService(name) {
  const child = spawn(node, [runner, name], {
    cwd: root,
    env: {
      ...process.env,
      BANKING_JARS_BUILT: '1',
      EVENT_TRANSPORT: kafkaEnabled ? 'kafka' : (process.env.EVENT_TRANSPORT || 'http'),
    },
    stdio: ['inherit', 'pipe', 'pipe'],
    windowsHide: true,
  });

  children.push({ name, child });
  child.stdout.on('data', data => process.stdout.write(`[${name}] ${data}`));
  child.stderr.on('data', data => process.stderr.write(`[${name}] ${data}`));
  child.on('error', error => console.error(`[${name}] Could not start: ${error.message}`));
  child.on('exit', code => {
    if (!stopping && code !== 0) {
      console.error(`[${name}] Stopped unexpectedly with exit code ${code}.`);
    }
  });
}

function waitForPort(port, name, timeoutMs = 60000, host = '127.0.0.1') {
  const startedAt = Date.now();

  return new Promise((resolve, reject) => {
    const tryConnection = () => {
      const socket = createConnection({ host, port });
      socket.once('connect', () => {
        socket.end();
        console.log(`${name} is listening on port ${port}.`);
        resolve();
      });
      socket.once('error', () => {
        socket.destroy();
        if (Date.now() - startedAt >= timeoutMs) {
          reject(new Error(`${name} did not open port ${port} within ${timeoutMs / 1000} seconds.`));
          return;
        }
        setTimeout(tryConnection, 1000);
      });
    };
    tryConnection();
  });
}

async function main() {
  console.log('Starting local banking backend services...');
  if (kafkaEnabled) {
    const bootstrap = (process.env.KAFKA_BOOTSTRAP_SERVERS || '127.0.0.1:9092').split(',')[0].trim();
    const match = /^(?:\[([^\]]+)\]|([^:]+)):(\d+)$/.exec(bootstrap);
    if (!match) throw new Error(`Invalid Kafka bootstrap server: ${bootstrap}`);
    const host = match[1] || match[2];
    const port = Number(match[3]);
    if (port < 1 || port > 65535) throw new Error(`Invalid Kafka bootstrap port: ${bootstrap}`);
    console.log(`Kafka event transport is enabled; checking ${bootstrap}...`);
    try {
      await waitForPort(port, 'Kafka', 5000, host);
    } catch {
      throw new Error(`Kafka is not reachable at ${bootstrap}. Start the broker or update KAFKA_BOOTSTRAP_SERVERS.`);
    }
  } else {
    console.log('Event transport: HTTP. Pass --kafka when a Kafka broker is available.');
  }
  console.log('Building fresh service JARs from this checkout first...');
  await buildFreshJars(root);
  startService('service-registry');
  await waitForPort(8761, 'Service Registry');

  const businessServices = [
    ['identity-service', 8081],
    ['accounts-ledger-service', 8082],
    ['payments-service', 8083],
    ['products-service', 8084],
    ['notification-service', 8085],
    ['audit-reporting-service', 8086],
  ];

  for (const [name] of businessServices) startService(name);
  await Promise.all(businessServices.map(([name, port]) => waitForPort(port, name)));

  startService('api-gateway');
  await waitForPort(8080, 'API Gateway');
  console.log('All backend services are running. Keep this terminal open.');
  console.log('Open the frontend separately with: cd frontend; npx.cmd ojet serve');
}

function stopAll() {
  if (stopping) return;
  stopping = true;
  console.log('\nStopping backend services...');
  for (const { child } of children) child.kill('SIGINT');
}

process.on('SIGINT', stopAll);
process.on('SIGTERM', stopAll);

main().catch(error => {
  console.error(`Startup failed: ${error.message}`);
  stopAll();
  process.exitCode = 1;
});
