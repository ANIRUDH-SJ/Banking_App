import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { parseEnv } from 'node:util';

export const servicePrefix = name => name.replaceAll('-', '_').toUpperCase() + '__';

export function readLocalEnv(root) {
  const path = join(root, '.env');
  return existsSync(path) ? parseEnv(readFileSync(path, 'utf8')) : {};
}

export function sharedEnv(values) {
  return Object.fromEntries(Object.entries(values).filter(([key]) => !key.includes('__')));
}

export function serviceEnv(name, values) {
  const prefix = servicePrefix(name);
  return Object.fromEntries(Object.entries(values)
    .filter(([key]) => key.startsWith(prefix))
    .map(([key, value]) => [key.slice(prefix.length), value]));
}

export function loadServiceConfig(root, name) {
  const path = join(root, '.local', `${name}.json`);
  const base = existsSync(path) ? JSON.parse(readFileSync(path, 'utf8')) : {};
  const values = readLocalEnv(root);
  // Scoped credentials are read only for this service; never export the whole file.
  return { ...base, ...sharedEnv(values), ...serviceEnv(name, values) };
}

export function serializeEnv(values) {
  return Object.entries(values).map(([key, value]) => {
    if (!/^[A-Z][A-Z0-9_]*$/.test(key) || typeof value !== 'string' || /[\r\n]/.test(value)) {
      throw new Error(`Invalid local environment entry: ${key}`);
    }
    const quote = !value.includes("'") ? "'" : !value.includes('"') ? '"' : null;
    if (!quote) throw new Error(`Use a value without mixed quotes for ${key}`);
    return `${key}=${quote}${value}${quote}`;
  }).join('\n') + '\n';
}

export function updateServiceEnv(root, name, settings) {
  if (!existsSync(join(root, '.env'))) return;
  const values = readLocalEnv(root);
  for (const [key, value] of Object.entries(settings)) values[servicePrefix(name) + key] = value;
  writeFileSync(join(root, '.env'), '# Private local configuration; never commit this file.\n' + serializeEnv(values), { mode: 0o600 });
}
