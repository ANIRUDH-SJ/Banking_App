import { spawn } from 'node:child_process';
import { posix } from 'node:path';

export function mavenBuildCommand(root, platform = process.platform) {
  const args = ['-B', '-ntp', '-DskipTests', 'clean', 'package'];
  if (platform === 'win32') {
    return { command: process.env.ComSpec || 'cmd.exe', args: ['/d', '/c', 'mvnw.cmd', ...args] };
  }
  return { command: posix.join(root, 'mvnw'), args };
}

export function buildFreshJars(root, options = {}) {
  const { command, args } = mavenBuildCommand(root, options.platform);
  const launch = options.spawnProcess || spawn;
  return new Promise((resolve, reject) => {
    const child = launch(command, args, { cwd: root, stdio: 'inherit', windowsHide: true });
    child.once('error', error => reject(new Error(`Could not build service JARs: ${error.message}`, { cause: error })));
    child.once('close', code => {
      if (code === 0) resolve();
      else reject(new Error(`Service JAR build failed (exit code ${code}); no services were started.`));
    });
  });
}
