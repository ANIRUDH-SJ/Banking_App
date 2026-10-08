import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { java17Environment, javaMajorVersion, kafkaLogDirectories, parseBootstrapServer } from './local-kafka.mjs';

test('parses IPv4, hostname and bracketed IPv6 Kafka bootstrap servers', () => {
  assert.deepEqual(parseBootstrapServer('127.0.0.1:9092'), { host: '127.0.0.1', port: 9092 });
  assert.deepEqual(parseBootstrapServer('localhost:19092'), { host: 'localhost', port: 19092 });
  assert.deepEqual(parseBootstrapServer('[::1]:9092'), { host: '::1', port: 9092 });
  assert.throws(() => parseBootstrapServer('localhost'), /Invalid Kafka bootstrap server/);
});

test('recognizes Java 17 version output and rejects other majors', () => {
  assert.equal(javaMajorVersion('java version "17.0.12"'), 17);
  assert.equal(javaMajorVersion('openjdk version "21.0.4"'), 21);
  assert.equal(javaMajorVersion('unknown'), undefined);
});

test('selects a configured Java 17 home', () => {
  const env = java17Environment({ JAVA_HOME: 'C:\\Java\\jdk-17', Path: 'C:\\Windows' }, {
    platform: 'win32',
    exists: path => path.endsWith('java.exe'),
    spawnSyncProcess: () => ({ status: 0, stdout: '', stderr: 'java version "17.0.12"' }),
  });
  assert.equal(env.JAVA_HOME, 'C:\\Java\\jdk-17');
  assert.match(env.Path, /^C:\\Java\\jdk-17\\bin;/);
});

test('resolves Kafka log directories from the KRaft config', () => {
  const root = mkdtempSync(join(tmpdir(), 'banking-kafka-'));
  try {
    const config = join(root, 'server.properties');
    mkdirSync(join(root, 'config'));
    writeFileSync(config, 'node.id=1\nlog.dirs=data, C:/absolute-kafka-data\n');
    assert.deepEqual(kafkaLogDirectories(config, root), [resolve(root, 'data'), resolve('C:/absolute-kafka-data')]);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
