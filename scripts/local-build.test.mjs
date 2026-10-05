import test from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { mavenBuildCommand, buildFreshJars } from './local-build.mjs';

test('uses the Maven wrapper and a clean package build on every platform', () => {
  const unix = mavenBuildCommand('/workspace', 'linux');
  assert.equal(unix.command, '/workspace/mvnw');
  assert.deepEqual(unix.args, ['-B', '-ntp', '-DskipTests', 'clean', 'package']);

  const windows = mavenBuildCommand('C:\\Banking_App', 'win32');
  assert.match(windows.command.toLowerCase(), /cmd\.exe$/);
  assert.deepEqual(windows.args, ['/d', '/c', 'mvnw.cmd', ...unix.args]);
});

test('rejects a failed build before services can start', async () => {
  let launched;
  const result = buildFreshJars('/workspace', {
    platform: 'linux',
    spawnProcess(command, args, options) {
      launched = { command, args, options };
      const child = new EventEmitter();
      queueMicrotask(() => child.emit('close', 1));
      return child;
    },
  });

  await assert.rejects(result, /build failed.*no services were started/i);
  assert.equal(launched.command, '/workspace/mvnw');
  assert.equal(launched.options.cwd, '/workspace');
});

test('accepts a completed build', async () => {
  await buildFreshJars('/workspace', {
    platform: 'linux',
    spawnProcess() {
      const child = new EventEmitter();
      queueMicrotask(() => child.emit('close', 0));
      return child;
    },
  });
});
