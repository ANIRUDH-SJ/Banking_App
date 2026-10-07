import { randomBytes } from 'node:crypto';
import { chmodSync, existsSync, lstatSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { createInterface } from 'node:readline/promises';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const configPath = join(root, '.local', 'notification-service.json');

export function resendSettings(address, apiKey) {
  const sender = address.trim();
  const key = apiKey.trim();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(sender)) {
    throw new Error('Enter a valid Resend sender address.');
  }
  if (!key.startsWith('re_') || key.length < 8) {
    throw new Error('Enter a Resend API key, not an account password.');
  }
  return {
    NOTIFICATION_EMAIL_PROVIDER: 'smtp',
    NOTIFICATION_EMAIL_FROM: sender,
    NOTIFICATION_LOG_MESSAGE_CONTENT: 'false',
    SMTP_HOST: 'smtp.resend.com',
    SMTP_PORT: '465',
    SMTP_USERNAME: 'resend',
    SMTP_PASSWORD: key,
    SMTP_AUTH: 'true',
    SMTP_STARTTLS_ENABLE: 'false',
    SMTP_STARTTLS_REQUIRED: 'false',
    SMTP_SSL_ENABLE: 'true',
    SMTP_SSL_CHECK_SERVER_IDENTITY: 'true',
    SMTP_TEST_CONNECTION: 'true',
  };
}

async function readSecret(prompt) {
  if (!process.stdin.isTTY) {
    throw new Error('Run this command in an interactive terminal so the API key stays hidden.');
  }
  process.stdout.write(prompt);
  process.stdin.setRawMode(true);
  process.stdin.resume();
  return new Promise((resolve, reject) => {
    let value = '';
    function finish(error) {
      process.stdin.off('data', onData);
      process.stdin.setRawMode(false);
      process.stdin.pause();
      process.stdout.write('\n');
      if (error) reject(error);
      else resolve(value);
    }
    function onData(chunk) {
      for (const character of chunk.toString('utf8')) {
        if (character === '\r' || character === '\n') return finish();
        if (character === '\u0003') return finish(new Error('Cancelled.'));
        if (character === '\u007f') value = value.slice(0, -1);
        else value += character;
      }
    }
    process.stdin.on('data', onData);
  });
}

async function main() {
  if (!existsSync(configPath)) {
    throw new Error('Create local service configuration with node scripts/init-local.mjs first.');
  }
  if (!lstatSync(configPath).isFile()) {
    throw new Error('The notification-service configuration must be a regular file.');
  }
  const current = JSON.parse(readFileSync(configPath, 'utf8'));
  const input = createInterface({ input: process.stdin, output: process.stdout });
  const address = await input.question('Verified sender (or onboarding@resend.dev for your own inbox only): ');
  input.close();
  const apiKey = await readSecret('Resend API key (input hidden): ');
  const updated = { ...current, ...resendSettings(address, apiKey) };
  const temporary = `${configPath}.${randomBytes(8).toString('hex')}.tmp`;
  writeFileSync(temporary, JSON.stringify(updated, null, 2) + '\n', { flag: 'wx', mode: 0o600 });
  chmodSync(temporary, 0o600);
  renameSync(temporary, configPath);
  console.log('Resend SMTP configured locally. Restart notification-service, then request a password-reset code.');
  console.log('The API key was not printed or committed. Keep .local private.');
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  main().catch(error => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
