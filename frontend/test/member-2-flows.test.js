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

function loadUiSupport() {
  let support;
  const source = fs.readFileSync(path.join(__dirname, '../src/js/services/ui-support.js'), 'utf8');
  vm.runInNewContext(source, {
    define: (names, factory) => {
      support = factory(ko, function ArrayDataProvider(rows) { this.rows = rows; }, { IntlNumberConverter: function () {} }, format, {});
    },
    Number,
    String,
    Date,
    Math,
    crypto: { randomUUID: () => 'test-idempotency-key' }
  }, { filename: 'ui-support.js' });
  return support;
}

const ui = loadUiSupport();

test('account tiles take their colour from the account', () => {
  assert.equal(ui.accountTone({ accountType: 'SAVINGS', currencyCode: 'INR' }), 'savings');
  assert.equal(ui.accountTone({ accountType: 'CURRENT', currencyCode: 'INR' }), 'current');
  assert.equal(ui.accountTone({ accountType: 'FIXED_DEPOSIT', currencyCode: 'INR' }), 'deposit');
  assert.equal(ui.accountTone({ accountType: 'SAVINGS', currencyCode: 'USD' }), 'wallet');
});

function PinAuthorization() {
  this.load = async () => {};
  this.use = () => {};
  this.reset = () => {};
  this.usingPin = () => false;
  this.available = () => false;
  this.lockText = () => '';
  this.cardLabel = () => '';
}

const tick = () => new Promise((resolve) => setImmediate(resolve));

test('bill payment checks the biller reference before requesting a code', async () => {
  const calls = [];
  const BillPayments = load('bill-payments.js', {
    knockout: ko,
    '../accUtils': { announce: () => {} },
    '../services/registry': { otp: { validateCode: () => '' }, accounts: {} },
    '../services/format': format,
    '../services/ui-support': ui,
    '../services/BillerService': {},
    '../services/BillPaymentService': {
      requestOtp: async (request) => {
        calls.push(request);
        return { challengeId: 'challenge-1' };
      }
    },
    '../services/pin-authorization': PinAuthorization
  });
  const screen = new BillPayments();
  screen.choose({
    billerId: 2,
    referenceLabel: 'Consumer number',
    referencePattern: '^[0-9]{5,20}$',
    referenceHint: 'Enter 5 to 20 digits.',
    minAmount: 1,
    maxAmount: 100000
  });
  screen.sourceAccountId(1);
  screen.amount(1000);
  screen.billReference('5678');
  screen.toReview();
  assert.equal(screen.flow.at(), 1);
  assert.equal(screen.referenceMessages()[0].summary, 'Enter 5 to 20 digits.');
  assert.equal(calls.length, 0);

  screen.billReference('56789');
  screen.toReview();
  assert.equal(screen.flow.at(), 2);
  screen.sendCode();
  await tick();
  assert.equal(screen.flow.at(), 3);
  assert.equal(calls[0].billReference, '56789');
});

test('accounts screen displays only bank-provided balances', async () => {
  const rows = [{ accountId: 11, accountNumber: '0000000011', currentBalance: 123, availableBalance: 100, currencyCode: 'INR', accountStatus: 'ACTIVE' }];
  const Accounts = load('accounts.js', {
    knockout: ko,
    '../accUtils': { announce: () => {} },
    '../services/registry': { accounts: { getAccounts: async () => rows } },
    '../services/format': format,
    '../services/ui-support': ui
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
    '../services/ui-support': ui,
    '../services/beneficiary-service': BeneficiaryService,
    '../services/fund-transfer-service': FundTransferService,
    '../services/pin-authorization': PinAuthorization
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
