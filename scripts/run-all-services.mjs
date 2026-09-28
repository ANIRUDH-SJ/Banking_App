import { spawn } from 'node:child_process';
import { createConnection } from 'node:net';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const node = process.execPath;
const runner = fileURLToPath(new URL('./run-service.mjs', import.meta.url));
const children = [];
let stopping = false;

function startService(name) {
  const child = spawn(node, [runner, name], {
    cwd: root,
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

function waitForPort(port, name, timeoutMs = 60000) {
  const startedAt = Date.now();

  return new Promise((resolve, reject) => {
    const tryConnection = () => {
      const socket = createConnection({ host: '127.0.0.1', port });
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
