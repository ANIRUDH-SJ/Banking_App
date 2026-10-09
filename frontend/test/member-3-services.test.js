const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function load(name, registry) {
  let service;
  const source = fs.readFileSync(path.join(__dirname, '../src/js/services', name), 'utf8');
  vm.runInNewContext(source, {
    define: (dependencies, factory) => {
      assert.deepEqual(Array.from(dependencies), ['./registry']);
      service = factory(registry);
    },
    URLSearchParams,
    encodeURIComponent,
    Object,
    String,
    Number,
    Array,
    Error
  }, { filename: name });
  return service;
}

test('bill payment uses the foundation API client and one-time-code service', async () => {
  const calls = [];
  const registry = {
    accounts: { getAccounts: async () => [{ accountId: 7 }] },
    otp: { requestBillPaymentChallenge: async (body) => { calls.push(['otp', body]); return { challengeId: 'challenge' }; } },
    apiClient: {
      get: async (path) => { calls.push(['get', path]); return []; },
      post: async (path, body) => { calls.push(['post', path, body]); return { transactionReference: 'BANK-1' }; }
    }
  };
  const service = load('BillPaymentService.js', registry);
  assert.equal((await service.listAccounts())[0].accountId, 7);
  await service.listHistory();
  await service.requestOtp({ sourceAccountId: '7', billerId: '8', billReference: 'ABC', amount: '25.50' });
  await service.submitPayment({ sourceAccountId: '7', billerId: '8', billReference: 'ABC', amount: '25.50', idempotencyKey: 'key', otpChallengeId: 'challenge', otpCode: '123456' });
  assert.equal(calls[0][1], '/api/v1/bill-payments');
  assert.equal(calls[1][1].sourceAccountId, 7);
  assert.equal(calls[2][2].otpChallengeId, 'challenge');
});

test('cards and loans use shared authenticated reads and status writes', async () => {
  const calls = [];
  const registry = {
    cards: { list: async () => ['card'] },
    loans: { list: async () => ['loan'] },
    accounts: { getAccounts: async () => ['account'] },
    apiClient: {
      patch: async (path, body) => { calls.push(['patch', path, body]); return { status: 'BLOCKED' }; },
      get: async (path) => { calls.push(['get', path]); return { content: [] }; },
      post: async (path, body) => { calls.push(['post', path, body]); return { transactionReference: 'L-1' }; }
    }
  };
  const cards = load('CardService.js', registry);
  const loans = load('LoanService.js', registry);
  assert.deepEqual(Array.from(await cards.listCards()), ['card']);
  assert.deepEqual(Array.from(await loans.listLoans()), ['loan']);
  await cards.changeStatus('7', 'BLOCK');
  await loans.listPayments('3', 1, 20);
  await loans.makePayment('3', { sourceAccountId: '9', amount: '25', idempotencyKey: 'key' });
  assert.equal(calls[0][1], '/api/v1/cards/7/status');
  assert.match(calls[1][1], /page=1/);
  assert.equal(calls[2][2].sourceAccountId, 9);
});

test('administrator requests use the foundation API client', async () => {
  const calls = [];
  const admin = load('AdminService.js', {
    apiClient: {
      get: async (path) => { calls.push(['get', path]); return { content: [] }; },
      patch: async (path, body) => { calls.push(['patch', path, body]); return { status: body.status }; }
    }
  });
  await admin.listUsers({ query: 'asha', page: 0, size: 10 });
  await admin.listAccounts({ status: 'ACTIVE' });
  await admin.listLoans({ status: 'ACTIVE' });
  await admin.loanSummary();
  await admin.updateUserStatus(4, 'DISABLED');
  await admin.updateAccountStatus(7, 'FROZEN');
  assert.match(calls[0][1], /^\/api\/v1\/admin\/users\?/);
  assert.match(calls[0][1], /query=asha/);
  assert.match(calls[1][1], /status=ACTIVE/);
  assert.match(calls[2][1], /admin\/loans.*status=ACTIVE/);
  assert.equal(calls[3][1], '/api/v1/admin/loans/summary');
  assert.equal(JSON.stringify(calls[4]), JSON.stringify(['patch', '/api/v1/admin/users/4/status', { status: 'DISABLED' }]));
  assert.equal(JSON.stringify(calls[5]), JSON.stringify(['patch', '/api/v1/admin/accounts/7/status', { status: 'FROZEN' }]));
});
