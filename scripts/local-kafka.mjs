import { spawn, spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync } from 'node:fs';
import { isAbsolute, join, resolve } from 'node:path';

export const defaultKafkaHome = process.platform === 'win32' ? 'C:\\kafka' : undefined;
export const defaultKafkaTopic = 'banking.events.v1';

function unquote(value) {
  return value?.replace(/^["']|["']$/g, '');
}

export function parseBootstrapServer(bootstrap) {
  const match = /^(?:\[([^\]]+)\]|([^:]+)):(\d+)$/.exec(bootstrap);
  if (!match) throw new Error(`Invalid Kafka bootstrap server: ${bootstrap}`);
  const port = Number(match[3]);
  if (port < 1 || port > 65535) throw new Error(`Invalid Kafka bootstrap port: ${bootstrap}`);
  return { host: match[1] || match[2], port };
}

export function javaMajorVersion(versionOutput) {
  const match = /version\s+"(\d+)(?:\.|\")/.exec(versionOutput);
  return match ? Number(match[1]) : undefined;
}

export function java17Environment(env = process.env, options = {}) {
  const platform = options.platform || process.platform;
  const fileExists = options.exists || existsSync;
  const run = options.spawnSyncProcess || spawnSync;
  const homes = [unquote(env.JAVA_HOME)];
  if (platform === 'win32') homes.push('C:\\Program Files\\Java\\jdk-17');

  for (const home of homes.filter(Boolean)) {
    const executable = join(home, 'bin', platform === 'win32' ? 'java.exe' : 'java');
    if (!fileExists(executable)) continue;
    const result = run(executable, ['-version'], { encoding: 'utf8', windowsHide: true });
    if (result.status !== 0 || javaMajorVersion(`${result.stdout || ''}\n${result.stderr || ''}`) !== 17) continue;
    const resolved = { ...env, JAVA_HOME: home };
    const pathKey = Object.keys(resolved).find(key => key.toLowerCase() === 'path') || (platform === 'win32' ? 'Path' : 'PATH');
    resolved[pathKey] = `${join(home, 'bin')}${platform === 'win32' ? ';' : ':'}${resolved[pathKey] || ''}`;
    return resolved;
  }

  const result = run('java', ['-version'], { encoding: 'utf8', windowsHide: true });
  if (result.status === 0 && javaMajorVersion(`${result.stdout || ''}\n${result.stderr || ''}`) === 17) return { ...env };
  throw new Error('Java 17 is required. Set JAVA_HOME to a JDK 17 installation.');
}

export function kafkaPaths(env = process.env, platform = process.platform) {
  const home = unquote(env.KAFKA_HOME) || defaultKafkaHome;
  if (!home) throw new Error('Set KAFKA_HOME to the Apache Kafka installation directory.');
  const windows = platform === 'win32';
  const bin = join(home, 'bin', ...(windows ? ['windows'] : []));
  const extension = windows ? '.bat' : '.sh';
  const paths = {
    home,
    config: unquote(env.KAFKA_CONFIG) || join(home, 'config', 'kraft', 'server.properties'),
    server: join(bin, `kafka-server-start${extension}`),
    storage: join(bin, `kafka-storage${extension}`),
    topics: join(bin, `kafka-topics${extension}`),
    platform,
  };
  for (const [name, path] of Object.entries(paths)) {
    if (name !== 'home' && name !== 'platform' && !existsSync(path)) {
      throw new Error(`Kafka ${name} file was not found: ${path}`);
    }
  }
  return paths;
}

export function kafkaLogDirectories(configPath, kafkaHome) {
  const line = readFileSync(configPath, 'utf8').split(/\r?\n/)
    .find(value => /^\s*log\.dirs\s*=/.test(value));
  if (!line) throw new Error(`Kafka config does not define log.dirs: ${configPath}`);
  return line.slice(line.indexOf('=') + 1).split(',').map(value => {
    const directory = value.trim();
    return isAbsolute(directory) ? resolve(directory) : resolve(kafkaHome, directory);
  });
}

function kafkaCommand(paths, script, args, env) {
  if (paths.platform === 'win32') {
    return {
      command: env.ComSpec || process.env.ComSpec || 'cmd.exe',
      args: ['/d', '/c', script, ...args],
    };
  }
  return { command: script, args };
}

export function runKafkaTool(paths, script, args, env) {
  const launch = kafkaCommand(paths, script, args, env);
  return new Promise((resolveRun, reject) => {
    const child = spawn(launch.command, launch.args, {
      cwd: paths.home,
      env,
      windowsHide: true,
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', data => { stdout += data; });
    child.stderr.on('data', data => { stderr += data; });
    child.once('error', error => reject(new Error(`Kafka command could not start: ${error.message}`, { cause: error })));
    child.once('close', code => {
      if (code === 0) resolveRun(stdout.trim());
      else reject(new Error((stderr || stdout || `Kafka command exited with code ${code}`).trim()));
    });
  });
}

export async function prepareKafkaStorage(paths, env) {
  const directories = kafkaLogDirectories(paths.config, paths.home);
  const ready = directories.every(directory => existsSync(join(directory, 'meta.properties')));
  if (ready) return false;

  for (const directory of directories) {
    if (existsSync(directory) && readdirSync(directory).length > 0) {
      throw new Error(`Kafka storage is not formatted but is not empty: ${directory}. Back it up before retrying.`);
    }
    mkdirSync(directory, { recursive: true });
  }

  const clusterId = await runKafkaTool(paths, paths.storage, ['random-uuid'], env);
  if (!clusterId) throw new Error('Kafka did not generate a KRaft cluster ID.');
  await runKafkaTool(paths, paths.storage, ['format', '-t', clusterId, '-c', paths.config], env);
  console.log(`Formatted an empty local Kafka KRaft store (${clusterId}).`);
  return true;
}

export function startKafkaBroker(paths, env) {
  const launch = kafkaCommand(paths, paths.server, [paths.config], env);
  const kafkaEnv = { ...env };
  if (paths.platform === 'win32' && !kafkaEnv.JAVA_TOOL_OPTIONS?.includes('jdk.net.unixdomain.tmpdir')) {
    const temp = join(paths.home, 'ktmp');
    mkdirSync(temp, { recursive: true });
    kafkaEnv.JAVA_TOOL_OPTIONS = `${kafkaEnv.JAVA_TOOL_OPTIONS || ''} -Djdk.net.unixdomain.tmpdir=${temp}`.trim();
  }
  kafkaEnv.KAFKA_HEAP_OPTS ||= '-Xms256m -Xmx512m';
  return spawn(launch.command, launch.args, {
    cwd: paths.home,
    env: kafkaEnv,
    windowsHide: true,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
}

export async function ensureSingleKafkaTopic(paths, env, topic = defaultKafkaTopic, options = {}) {
  const partitions = String(options.partitions || env.KAFKA_TOPIC_PARTITIONS || '3');
  const replication = String(options.replicationFactor || env.KAFKA_REPLICATION_FACTOR || '1');
  let topics = (await runKafkaTool(paths, paths.topics,
    ['--bootstrap-server', options.bootstrap || '127.0.0.1:9092', '--list'], env))
    .split(/\r?\n/).map(value => value.trim()).filter(Boolean);

  if (!topics.includes(topic)) {
    await runKafkaTool(paths, paths.topics, [
      '--bootstrap-server', options.bootstrap || '127.0.0.1:9092',
      '--create', '--if-not-exists', '--topic', topic,
      '--partitions', partitions, '--replication-factor', replication,
    ], env);
    topics = (await runKafkaTool(paths, paths.topics,
      ['--bootstrap-server', options.bootstrap || '127.0.0.1:9092', '--list'], env))
      .split(/\r?\n/).map(value => value.trim()).filter(Boolean);
  }

  const applicationTopics = topics.filter(value => !value.startsWith('__'));
  const unexpected = applicationTopics.filter(value => value !== topic);
  if (unexpected.length || applicationTopics.length !== 1) {
    throw new Error(`Kafka must contain only the application topic ${topic}; found: ${applicationTopics.join(', ') || '(none)'}.`);
  }
  console.log(`Kafka topic ready: ${topic} (${partitions} partitions requested).`);
}
