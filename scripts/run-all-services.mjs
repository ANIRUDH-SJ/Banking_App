import { spawn, spawnSync } from 'node:child_process';
import { createConnection } from 'node:net';
import { fileURLToPath } from 'node:url';
import { buildFreshJars } from './local-build.mjs';
import {
  defaultKafkaTopic,
  ensureSingleKafkaTopic,
  java17Environment,
  kafkaPaths,
  parseBootstrapServer,
  prepareKafkaStorage,
  startKafkaBroker,
} from './local-kafka.mjs';
import { readLocalEnv, sharedEnv } from './local-env.mjs';
import { serviceLaunch } from './service-launch.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const common = sharedEnv(readLocalEnv(root));
let serviceEnv = { ...process.env, ...common };
const eventTransport = process.argv.includes('--kafka') || (common.EVENT_TRANSPORT || process.env.EVENT_TRANSPORT) === 'kafka'
  ? 'kafka' : (common.EVENT_TRANSPORT || process.env.EVENT_TRANSPORT || 'http');
const servicePorts = [
  ['service-registry', 8761],
  ['identity-service', 8081],
  ['accounts-ledger-service', 8082],
  ['payments-service', 8083],
  ['products-service', 8084],
  ['notification-service', 8085],
  ['audit-reporting-service', 8086],
  ['api-gateway', 8080],
];
const children = [];
let stopping = false;
let startupFailure;
let ready = false;

function serviceFailed(error) {
  if (stopping || startupFailure) return;
  startupFailure = error;
  if (ready) {
    console.error(error.message);
    process.exitCode = 1;
    stopAll();
  }
}

function trackChild(name, child, { processTree = false } = {}) {
  children.push({ name, child, processTree });
  child.stdout?.on('data', data => process.stdout.write(`[${name}] ${data}`));
  child.stderr?.on('data', data => process.stderr.write(`[${name}] ${data}`));
  child.on('error', error => serviceFailed(new Error(`${name} could not start: ${error.message}`)));
  child.on('exit', (code, signal) => {
    if (!stopping) serviceFailed(new Error(`${name} exited unexpectedly (${signal || `code ${code}`}).`));
  });
  return child;
}

function startService(name) {
  const launch = serviceLaunch(root, name, { env: serviceEnv, eventTransport });
  // Own the Java process directly. On Windows, killing a Node wrapper leaves its
  // Java grandchild alive, holding the JAR and port after the launcher exits.
  const child = spawn(launch.command, launch.args, {
    ...launch.options,
    stdio: ['inherit', 'pipe', 'pipe'],
    windowsHide: true,
  });

  return trackChild(name, child);
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
        if (startupFailure) {
          reject(startupFailure);
          return;
        }
        if (stopping) {
          reject(new Error('Startup was stopped.'));
          return;
        }
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

function portIsOpen(port, host = '127.0.0.1') {
  return new Promise(resolve => {
    const socket = createConnection({ host, port });
    socket.setTimeout(1000);
    socket.once('connect', () => { socket.destroy(); resolve(true); });
    socket.once('error', () => { socket.destroy(); resolve(false); });
    socket.once('timeout', () => { socket.destroy(); resolve(false); });
  });
}

async function requireFreeServicePorts() {
  const checks = await Promise.all(servicePorts.map(async ([name, port]) =>
    await portIsOpen(port) ? `${name} (${port})` : null));
  const occupied = checks.filter(Boolean);
  if (occupied.length) {
    throw new Error(`Backend already running or ports occupied: ${occupied.join(', ')}. `
      + 'Stop the existing backend before rebuilding; do not start a second copy.');
  }
}

async function waitForHealth(name, port, timeoutMs = 120000) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    if (startupFailure) throw startupFailure;
    if (stopping) throw new Error('Startup was stopped.');
    try {
      const response = await fetch(`http://127.0.0.1:${port}/actuator/health`, {
        signal: AbortSignal.timeout(2000),
      });
      if (response.ok && (await response.json()).status === 'UP') {
        if (startupFailure) throw startupFailure;
        console.log(`${name} is healthy on port ${port}.`);
        return;
      }
    } catch {
      // A service may still be starting; its process exit is checked above.
    }
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new Error(`${name} did not become healthy on port ${port} within ${timeoutMs / 1000} seconds.`);
}

async function main() {
  console.log('Starting local banking backend services...');
  serviceEnv = java17Environment(serviceEnv);
  console.log(`Java runtime: ${serviceEnv.JAVA_HOME || 'Java 17 from PATH'}`);
  await requireFreeServicePorts();
  if (eventTransport === 'kafka') {
    const bootstrap = (common.KAFKA_BOOTSTRAP_SERVERS || process.env.KAFKA_BOOTSTRAP_SERVERS || '127.0.0.1:9092').split(',')[0].trim();
    const { host, port } = parseBootstrapServer(bootstrap);
    const localBroker = ['127.0.0.1', 'localhost', '::1'].includes(host);
    const paths = kafkaPaths(serviceEnv);
    if (!await portIsOpen(port, host)) {
      if (!localBroker) {
        throw new Error(`Kafka is not reachable at ${bootstrap}; automatic startup is available only for a local broker.`);
      }
      console.log(`Kafka is not running at ${bootstrap}; starting it from ${paths.home}...`);
      await prepareKafkaStorage(paths, serviceEnv);
      trackChild('kafka', startKafkaBroker(paths, serviceEnv), { processTree: process.platform === 'win32' });
      await waitForPort(port, 'Kafka', 60000, host);
    } else {
      console.log(`Reusing Kafka already listening at ${bootstrap}.`);
    }
    await ensureSingleKafkaTopic(paths, serviceEnv,
      common.KAFKA_EVENT_TOPIC || process.env.KAFKA_EVENT_TOPIC || defaultKafkaTopic,
      { bootstrap });
  } else {
    console.log('Event transport: HTTP. Pass --kafka when a Kafka broker is available.');
  }
  console.log('Building fresh service JARs from this checkout first...');
  await buildFreshJars(root, { env: serviceEnv });
  if (stopping) return;
  startService('service-registry');
  await waitForHealth('Service Registry', 8761);

  const businessServices = servicePorts.slice(1, -1);

  // Starting six Spring contexts at once can exhaust a development laptop.
  for (const [name, port] of businessServices) {
    startService(name);
    await waitForHealth(name, port);
  }

  startService('api-gateway');
  await waitForHealth('API Gateway', 8080);
  ready = true;
  console.log('All backend services are running. Keep this terminal open.');
  console.log('Open the frontend separately with: cd frontend; npm.cmd run serve');
}

function stopAll() {
  if (stopping) return;
  stopping = true;
  console.log('\nStopping backend services...');
  for (const { child, processTree } of [...children].reverse()) {
    if (child.exitCode !== null || !child.pid) continue;
    if (processTree && process.platform === 'win32') {
      spawnSync('taskkill', ['/pid', String(child.pid), '/t', '/f'], { windowsHide: true, stdio: 'ignore' });
    } else {
      child.kill('SIGTERM');
    }
  }
}

process.on('SIGINT', () => { process.exitCode = 130; stopAll(); });
process.on('SIGTERM', () => { process.exitCode = 143; stopAll(); });

main().catch(error => {
  console.error(`Startup failed: ${error.message}`);
  stopAll();
  process.exitCode = 1;
});
