'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const load = require('./load-amd');

function memoryStorage() {
  const data = new Map();
  return {
    getItem(key) {
      return data.has(key) ? data.get(key) : null;
    },
    setItem(key, value) {
      data.set(key, String(value));
    },
    removeItem(key) {
      data.delete(key);
    }
  };
}

function token(expSeconds) {
  const header = Buffer.from(JSON.stringify({ alg: 'none' })).toString('base64url');
  const payload = Buffer.from(JSON.stringify({ exp: expSeconds, token_use: 'access' })).toString('base64url');
  return header + '.' + payload + '.sig';
}

test('validation matches the identity request rules', function () {
  const validation = load('services/validation-service.js');
  const registration = validation.registration({
    username: 'ab',
    email: 'not-an-email',
    password: 'short',
    firstName: '',
    lastName: 'Sharma',
    dateOfBirth: '2999-01-01',
    mobileNumber: '123'
  }, new Date('2026-10-05T00:00:00Z'));
  assert.equal(registration.valid, false);
  assert.match(registration.fieldErrors.username, /3 to 100/);
  assert.match(registration.fieldErrors.password, /12 to 128/);
  assert.match(registration.fieldErrors.dateOfBirth, /past/);
  assert.match(registration.fieldErrors.mobileNumber, /mobile/);

  const reset = validation.passwordResetConfirm({
    code: '12345',
    newPassword: 'a-new-strong-password',
    confirmPassword: 'different-password'
  });
  assert.match(reset.fieldErrors.code, /6-digit/);
  assert.match(reset.fieldErrors.confirmPassword, /same password/);
  assert.equal(validation.otpCode('123456'), '');
});

test('session stores only the access session and expires it', function () {
  const SessionService = load('services/session-service.js');
  const storage = memoryStorage();
  let now = 1_000_000;
  const session = new SessionService({
    storage: storage,
    now: function () { return now; },
    warningWindowMs: 60_000,
    schedule: function () { return 1; },
    cancel: function () {}
  });
  const warnings = [];
  const expired = [];
  session.on('warning', function (remaining) { warnings.push(remaining); });
  session.on('expired', function () { expired.push(true); });

  const expiresAtSeconds = Math.floor((now + 120_000) / 1000);
  session.establish({
    accessToken: token(expiresAtSeconds),
    tokenType: 'Bearer',
    userId: 7,
    username: 'asha',
    roles: ['CUSTOMER'],
    password: 'must-not-be-stored',
    qrCodeDataUri: 'must-not-be-stored'
  });
  const stored = JSON.parse(storage.getItem(SessionService.STORAGE_KEY));
  assert.deepEqual(Object.keys(stored).sort(), ['accessToken', 'expiresAt', 'roles', 'tokenType', 'userId', 'username']);
  assert.equal(stored.password, undefined);
  assert.equal(session.authorizationHeader(), 'Bearer ' + stored.accessToken);

  now = stored.expiresAt - 30_000;
  session.tick();
  assert.equal(warnings.length, 1);
  session.tick();
  assert.equal(warnings.length, 1);

  now = stored.expiresAt + 1;
  session.tick();
  assert.equal(expired.length, 1);
  assert.equal(session.current(), null);
  assert.equal(storage.getItem(SessionService.STORAGE_KEY), null);
});

test('route guard separates anonymous, authenticator, customer, and admin routes', function () {
  const guard = load('services/route-guard-service.js');
  const customer = { accessToken: 'token', roles: ['CUSTOMER'] };
  const admin = { accessToken: 'token', roles: ['ADMIN'] };

  assert.equal(guard.evaluate('dashboard', null, null).redirect, 'login');
  assert.equal(guard.evaluate('login', customer, null).redirect, 'dashboard');
  assert.equal(guard.evaluate('accounts', null, null).redirect, 'login');
  assert.equal(guard.evaluate('totp-setup', null, null).redirect, 'login');
  assert.equal(guard.evaluate('totp-setup', null, { kind: 'setup', credentials: { usernameOrEmail: 'asha' } }).allow, true);
  assert.equal(guard.evaluate('totp-verify', null, { kind: 'verify', challengeId: 'challenge' }).allow, true);
  assert.equal(guard.evaluate('admin', customer, null).reason, 'forbidden');
  assert.equal(guard.evaluate('admin', admin, null).allow, true);
  assert.equal(Array.prototype.map.call(guard.navigationFor(customer), function (item) {
    return item.path;
  }).join(','), 'dashboard,profile,notifications');
  const adminPaths = Array.prototype.map.call(guard.navigationFor(admin), function (item) {
    return item.path;
  });
  assert.equal(adminPaths[adminPaths.length - 1], 'admin');
});

test('api client sends bearer tokens only outside auth and clears a rejected session', async function () {
  const ApiClientService = load('services/api-client-service.js');
  const calls = [];
  let discarded = false;
  const client = new ApiClientService({
    baseUrl: 'http://localhost:8080',
    session: {
      authorizationHeader: function () { return 'Bearer secret-token'; },
      discardUnauthorized: function () { discarded = true; }
    },
    fetchImpl: function (url, options) {
      calls.push({ url: url, options: options });
      if (url.endsWith('/api/v1/profile')) {
        return Promise.resolve({
          ok: false,
          status: 401,
          text: function () {
            return Promise.resolve(JSON.stringify({
              code: 'UNAUTHORIZED',
              message: 'Authentication is required.',
              fieldErrors: {}
            }));
          }
        });
      }
      return Promise.resolve({
        ok: true,
        status: 204,
        text: function () { return Promise.resolve(''); }
      });
    }
  });

  await client.post('/api/v1/auth/login', { usernameOrEmail: 'asha', password: 'secret-password' });
  assert.equal(calls[0].options.headers.Authorization, undefined);
  assert.equal(calls[0].options.body.includes('secret-password'), true);
  assert.equal(discarded, false);

  await assert.rejects(client.get('/api/v1/profile'), function (error) {
    assert.equal(error.code, 'UNAUTHORIZED');
    assert.equal(error.message, 'Authentication is required.');
    return true;
  });
  assert.equal(calls[1].options.headers.Authorization, 'Bearer secret-token');
  assert.equal(discarded, true);
});

test('auth service keeps setup credentials in memory and drops them after confirmation', async function () {
  const AuthService = load('services/auth-service.js');
  const posts = [];
  const flow = {
    state: null,
    rememberSetup: function (credentials) { this.state = { kind: 'setup', credentials: credentials }; },
    rememberChallenge: function (challengeId) { this.state = { kind: 'verify', challengeId: challengeId }; },
    current: function () { return this.state; },
    clear: function () { this.state = null; }
  };
  const auth = new AuthService({
    post: function (path, body) {
      posts.push({ path: path, body: body });
      if (path.endsWith('/login')) {
        return Promise.resolve({ challengeId: null, status: 'TOTP_SETUP_REQUIRED' });
      }
      return Promise.resolve(null);
    }
  }, flow);

  await auth.login(' asha ', 'correct-horse-battery');
  assert.equal(flow.state.credentials.password, 'correct-horse-battery');
  await auth.confirmTotpSetup('123456');
  assert.equal(flow.state, null);
  assert.equal(posts[1].body.code, '123456');
  assert.equal(posts[1].body.credentials.usernameOrEmail, 'asha');
});

test('otp service targets the existing challenge endpoints', async function () {
  const OtpService = load('services/otp-service.js');
  const validation = load('services/validation-service.js');
  const paths = [];
  const otp = new OtpService({
    post: function (path, body) {
      paths.push({ path: path, body: body });
      return Promise.resolve({ challengeId: 'abc', status: 'OTP_SENT' });
    }
  }, validation);

  await otp.transferChallenge({ sourceAccountId: 1 });
  await otp.billPaymentChallenge({ billerId: 2 });
  await otp.beneficiaryActivationChallenge('10/20');
  await otp.forexChallenge('quote 1');
  assert.deepEqual(paths.map(function (call) { return call.path; }), [
    '/api/v1/transfers/otp-challenges',
    '/api/v1/bill-payments/otp-challenges',
    '/api/v1/beneficiaries/10%2F20/activation-challenges',
    '/api/v1/forex/quotes/quote%201/otp-challenges'
  ]);
  assert.equal(otp.validateCode('000000'), '');
});

test('dashboard summaries report a provider failure without dropping the others', async function () {
  const slots = load('services/dashboard-slots.js');
  slots.registerSummary({
    id: 'accounts',
    title: 'Accounts',
    load: function () { return '2 accounts'; }
  });
  slots.registerSummary({
    id: 'cards',
    title: 'Cards',
    load: function () { return Promise.reject(new Error('unavailable')); }
  });
  const summaries = await slots.summaries();
  assert.equal(summaries[0].status, 'ready');
  assert.equal(summaries[0].value, '2 accounts');
  assert.equal(summaries[1].status, 'error');
});
