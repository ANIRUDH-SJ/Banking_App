const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function observable(initial) {
  let value = initial;
  return function (next) {
    if (arguments.length) value = next;
    return value;
  };
}

const ko = {
  observable,
  observableArray: observable,
  pureComputed: (compute) => compute
};

const format = {
  asList: (value) => Array.isArray(value) ? value : (value?.content || []),
  formatMoney: (amount, currency) => `${currency} ${amount}`,
  formatDateTime: () => 'now',
  maskAccount: (number) => `•••• ${String(number).slice(-4)}`,
  labelize: (value) => value
};

function load(name, dependencies) {
  let ViewModel;
  const source = fs.readFileSync(path.join(__dirname, '../src/js/viewModels', name), 'utf8');
  vm.runInNewContext(source, {
    define: (names, factory) => {
      ViewModel = factory(...Array.from(names, (dependency) => dependencies[dependency]));
    },
    Promise,
    Number,
    String,
    Date,
    Math,
    crypto: { randomUUID: () => 'test-idempotency-key' },
    document: { title: '' },
    URLSearchParams
  }, { filename: name });
  return ViewModel;
}

const tick = () => new Promise((resolve) => setImmediate(resolve));

test('accounts screen displays only bank-provided balances', async () => {
  const rows = [{ accountId: 11, accountNumber: '0000000011', currentBalance: 123, availableBalance: 100, currencyCode: 'INR', accountStatus: 'ACTIVE' }];
  const Accounts = load('accounts.js', {
    knockout: ko,
    '../accUtils': { announce: () => {} },
    '../services/registry': { accounts: { getAccounts: async () => rows } },
    '../services/format': format
  });
  const screen = new Accounts();
  assert.equal(screen.accounts().length, 0);
  screen.refreshAccounts();
  await tick();
  assert.equal(screen.accounts()[0].currentBalance, 123);
  assert.equal(screen.loading(), false);
});

test('transfer receipt comes only from the server after a real OTP challenge', async () => {
  const calls = [];
  const registry = {
    apiClient: {},
    accounts: { getAccounts: async () => [{ accountId: 4, accountType: 'SAVINGS', accountNumber: '0004', accountStatus: 'ACTIVE', currencyCode: 'INR', availableBalance: 100 }] },
    otp: {
      requestTransferChallenge: async (...args) => { calls.push(['challenge', ...args]); return { challengeId: 'server-challenge' }; },
      validateCode: (code) => /^\d{6}$/.test(code) ? '' : 'Invalid code'
    }
  };
  const BeneficiaryService = function () {
    this.list = async () => [{ beneficiaryId: 9, nickname: 'Home', maskedAccountNumber: '•••• 1234', status: 'ACTIVE' }];
  };
  const FundTransferService = function () {
    this.transfer = async (request) => {
      calls.push(['transfer', request]);
      return { transactionReference: 'BANK-42', status: 'PENDING', amount: 25, currencyCode: 'INR' };
    };
  };
  const Transfer = load('transfer.js', {
    knockout: ko,
    '../accUtils': { announce: () => {} },
    '../services/registry': registry,
    '../services/format': format,
    '../services/beneficiary-service': BeneficiaryService,
    '../services/fund-transfer-service': FundTransferService
  });
  const screen = new Transfer();
  await screen.refreshOptions();
  screen.amount('25');
  screen.reviewTransfer();
  assert.equal(screen.step(), 'review');
  assert.equal(screen.receipt(), null);
  screen.requestOtp();
  await tick();
  assert.equal(screen.step(), 'otp');
  assert.equal(screen.otpChallengeId(), 'server-challenge');
  screen.otpCode('123456');
  screen.confirmTransfer();
  await tick();
  assert.equal(screen.receipt().transactionReference, 'BANK-42');
  assert.equal(screen.receipt().status, 'PENDING');
  assert.equal(calls[1][1].idempotencyKey, 'test-idempotency-key');
  assert.equal(calls[1][1].otpChallengeId, 'server-challenge');
});
