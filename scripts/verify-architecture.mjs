import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import assert from 'node:assert/strict';
const root = fileURLToPath(new URL('../', import.meta.url));
function files(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    if (['target', '.git', 'node_modules'].includes(entry.name)) return [];
    const path = join(directory, entry.name);
    return entry.isDirectory() ? files(path) : [path];
  });
}
const owners = {
  auth: 'identity-service', user: 'identity-service', role: 'identity-service', customer: 'identity-service', otp: 'identity-service', totp: 'identity-service',
  account: 'accounts-ledger-service', bank: 'accounts-ledger-service', branch: 'accounts-ledger-service', transaction: 'accounts-ledger-service', statement: 'accounts-ledger-service', ledger: 'accounts-ledger-service',
  beneficiary: 'payments-service', biller: 'payments-service', payment: 'payments-service',
  card: 'products-service', loan: 'products-service', notification: 'notification-service', admin: 'audit-reporting-service',
};
for (const service of readdirSync(join(root, 'services'))) {
  const source = files(join(root, 'services', service, 'src'));
  for (const file of source.filter(f => f.endsWith('.java'))) {
    const code = readFileSync(file, 'utf8');
    for (const [, pkg] of code.matchAll(/import (?:static )?com\.netbanking\.(\w+)\./g)) {
      assert(!owners[pkg] || owners[pkg] === service, `${file} imports another service's ${pkg} package`);
    }
    assert(!code.includes('@LoadBalanced') && !code.includes('lb://'), `${file} uses load balancing`);
  }
  const migrations = source.filter(f => f.includes('/db/migration/') && f.endsWith('.sql')).map(f => readFileSync(f, 'utf8')).join('\n');
  const tables = new Set([...migrations.matchAll(/CREATE TABLE\s+(\w+)/gi)].map(m => m[1].toLowerCase()));
  for (const [, table] of migrations.matchAll(/REFERENCES\s+([\w.]+)/gi)) {
    assert(tables.has(table.toLowerCase()), `${service} references a table outside its schema: ${table}`);
  }
  assert(!/\b(?:GRANT|CREATE SYNONYM)\b/i.test(migrations), `${service} migration grants cross-schema access`);
}
for (const file of files(join(root, 'libraries')).filter(f => f.endsWith('.java') && f.includes('/src/main/'))) {
  assert(!/@Entity\b|@Repository\b/.test(readFileSync(file, 'utf8')), `Shared business persistence in ${file}`);
}
console.log('Verified service source boundaries, private-schema foreign keys, and shared runtime isolation.');
