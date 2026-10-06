const test = require('node:test');
const assert = require('node:assert/strict');

const { ApiError, parseApiError, fieldMessage } = require('../src/js/services/api-error');
const ValidationService = require('../src/js/services/validation-service');
const SessionService = require('../src/js/services/session-service');
const ApiClientService = require('../src/js/services/api-client-service');
const AuthService = require('../src/js/services/auth-service');
const OtpService = require('../src/js/services/otp-service');
const routeGuard = require('../src/js/services/route-guard-service');
const format = require('../src/js/services/format');
const authFlow = require('../src/js/services/auth-flow-state');

function memoryStorage() {
  const map = new Map();
  return {
    getItem(key) { return map.has(key) ? map.get(key) : null; },
    setItem(key, value) { map.set(key, String(value)); },
    removeItem(key) { map.delete(key); }
  };
}

function token(exp) {
  const header = Buffer.from(JSON.stringify({ alg: 'none' })).toString('base64url');
  const body = Buffer.from(JSON.stringify({ exp: exp, token_use: 'access' })).toString('base64url');
  return header + '.' + body + '.sig';
}

function jsonResponse(status, body, extraHeaders) {
  const headers = Object.assign({ 'content-type': 'application/json' }, extraHeaders || {});
  return {
    ok: status >= 200 && status < 300,
    status: status,
    headers: { get(name) { return headers[name.toLowerCase()] || ''; } },
    json() { return Promise.resolve(body); },
    text() { return Promise.resolve(JSON.stringify(body)); }
  };
}

test('validation matches the identity service rules', () => {
  const validation = new ValidationService();
  assert.equal(validation.password('short'), 'Use 12 to 128 characters.');
  assert.equal(validation.password('a-sufficiently-long-password'), '');
  assert.equal(validation.passwordMatch('a-sufficiently-long-password', 'other-password-value'), 'Passwords do not match.');
  assert.equal(validation.mobile('+91 98765 43210'), '');
  assert.equal(validation.mobile('123'), 'Enter a mobile number using digits, spaces, or hyphens.');
  assert.equal(validation.otp('12345'), 'Enter the 6-digit code.');
  assert.equal(validation.otp('123456'), '');
  assert.equal(validation.email('asha@example.com'), '');
  assert.equal(validation.username('ab'), 'Use 3 to 100 characters for the user ID.');
  assert.match(validation.dateOfBirth('2099-01-01'), /past/);
  assert.equal(validation.dateOfBirth('1990-04-02', new Date('2026-10-05T00:00:00')), '');
});

test('api errors preserve the server field map and correlation id', () => {
  const error = parseApiError(400, {
    timestamp: '2026-10-05T00:00:00Z',
    status: 400,
    code: 'VALIDATION_FAILED',
    message: 'Request validation failed.',
    path: '/api/v1/auth/register',
    correlationId: 'corr-1',
    fieldErrors: { email: 'Enter a valid email address.' }
  }, '/api/v1/auth/register', '');
  assert.ok(error instanceof ApiError);
  assert.equal(error.code, 'VALIDATION_FAILED');
  assert.equal(fieldMessage(error, 'email'), 'Enter a valid email address.');
  assert.equal(error.correlationId, 'corr-1');
});

test('session stores the access token and drops it at expiry', () => {
  let now = 1_000_000;
  const session = new SessionService(memoryStorage(), function () { return now; });
  const saved = session.save({
    accessToken: token(2000),
    tokenType: 'Bearer',
    userId: 7,
    username: 'asha',
    roles: ['CUSTOMER']
  });
  assert.equal(saved.username, 'asha');
  assert.equal(session.getAccessToken(), saved.accessToken);
  assert.equal(session.hasRole('CUSTOMER'), true);
  assert.equal(session.hasRole('ADMIN'), false);
  now = 2000 * 1000 + 1;
  assert.equal(session.getSession(), null);
  assert.equal(session.getAccessToken(), null);
});

test('session rejects a token without an expiry', () => {
  const session = new SessionService(memoryStorage());
  const header = Buffer.from('{}').toString('base64url');
  const body = Buffer.from(JSON.stringify({ token_use: 'access' })).toString('base64url');
  assert.equal(session.save({ accessToken: header + '.' + body + '.sig', roles: [] }), null);
});

test('api client sends the bearer token and surfaces validation errors', async () => {
  const calls = [];
  const session = {
    token: 'access-token',
    getAccessToken() { return this.token; },
    getSession() { return this.token ? { accessToken: this.token, tokenType: 'Bearer', expiresAt: Date.now() + 10000, roles: ['CUSTOMER'] } : null; },
    clear() { this.token = null; }
  };
  let unauthorized = 0;
  const client = new ApiClientService({
    session: session,
    baseUrl: 'http://bank.test',
    onUnauthorized() { unauthorized += 1; },
    fetch(url, init) {
      calls.push({ url: url, init: init });
      if (url.endsWith('/missing')) {
        return Promise.reject(new Error('offline'));
      }
      if (url.endsWith('/health')) {
        return Promise.resolve(jsonResponse(200, { status: 'UP' }));
      }
      if (url.endsWith('/register')) {
        return Promise.resolve(jsonResponse(400, {
          status: 400,
          code: 'VALIDATION_FAILED',
          message: 'Request validation failed.',
          fieldErrors: { username: 'Use 3 to 100 characters for the user ID.' }
        }));
      }
      if (url.endsWith('/private')) {
        return Promise.resolve(jsonResponse(401, {
          status: 401,
          code: 'UNAUTHORIZED',
          message: 'Authentication is required.'
        }));
      }
      return Promise.resolve({
        ok: true,
        status: 204,
        headers: { get(name) { return name.toLowerCase() === 'content-type' ? 'text/plain' : ''; } },
        json() { return Promise.resolve(null); },
        text() { return Promise.resolve(''); }
      });
    }
  });

  await client.get('/api/v1/health', { auth: false });
  assert.equal(calls[0].init.headers.Authorization, undefined);
  await assert.rejects(client.post('/api/v1/auth/register', { username: 'a' }, { auth: false }), (error) => {
    assert.equal(error.code, 'VALIDATION_FAILED');
    assert.equal(fieldMessage(error, 'username'), 'Use 3 to 100 characters for the user ID.');
    return true;
  });

  const empty = await client.post('/api/v1/beneficiaries/4/activation-challenges');
  assert.equal(empty, null);
  assert.match(calls[2].init.headers.Authorization, /^Bearer access-token$/);

  await assert.rejects(client.get('/api/v1/private'), (error) => {
    assert.equal(error.status, 401);
    return true;
  });
  assert.equal(unauthorized, 1);
  assert.equal(session.token, null);

  await assert.rejects(client.get('/missing', { auth: false }), (error) => {
    assert.equal(error.code, 'NETWORK');
    return true;
  });
});

test('auth service uses the public identity endpoints', async () => {
  const calls = [];
  const client = {
    post(path, body, options) {
      calls.push({ path: path, body: body, options: options });
      return Promise.resolve({ ok: true });
    }
  };
  const auth = new AuthService(client);
  await auth.login('asha', 'a-sufficiently-long-password');
  await auth.verifyTotp('challenge', '123456');
  await auth.confirmTotp('asha', 'a-sufficiently-long-password', '654321');
  await auth.requestPasswordReset('asha@example.com');
  assert.deepEqual(calls.map((call) => call.path), [
    '/api/v1/auth/login',
    '/api/v1/auth/login/verify-totp',
    '/api/v1/auth/totp/confirm',
    '/api/v1/auth/password-reset/challenges'
  ]);
  assert.equal(calls[0].options.auth, false);
  assert.equal(calls[2].body.credentials.usernameOrEmail, 'asha');
  assert.equal(calls[2].body.code, '654321');
});

test('otp service requests payment challenges and checks the code', async () => {
  const calls = [];
  const client = {
    post(path, body) {
      calls.push({ path: path, body: body });
      return Promise.resolve({ challengeId: 'c-1', status: 'OTP_SENT' });
    }
  };
  const otp = new OtpService(client, new ValidationService());
  const transfer = await otp.requestTransferChallenge(10, 20, '150.50');
  await otp.requestBeneficiaryActivation(20);
  await otp.requestBillPaymentChallenge({
    sourceAccountId: 10,
    billerId: 3,
    billReference: 'BILL-1',
    amount: '80.00'
  });
  await otp.requestForexChallenge('quote/1');
  assert.equal(transfer.status, 'OTP_SENT');
  assert.deepEqual(calls.map((call) => call.path), [
    '/api/v1/transfers/otp-challenges',
    '/api/v1/beneficiaries/20/activation-challenges',
    '/api/v1/bill-payments/otp-challenges',
    '/api/v1/forex/quotes/quote%2F1/otp-challenges'
  ]);
  assert.equal(calls[0].body.amount, '150.50');
  assert.equal(otp.validateCode('12'), 'Enter the 6-digit code.');
});

test('route guard sends customers and administrators to their own desks', () => {
  const now = 5_000;
  const customer = { accessToken: 't', expiresAt: 9_000, roles: ['CUSTOMER'] };
  const admin = { accessToken: 't', expiresAt: 9_000, roles: ['ADMIN'] };
  const both = { accessToken: 't', expiresAt: 9_000, roles: ['CUSTOMER', 'ADMIN'] };
  assert.equal(routeGuard.evaluate('dashboard', null, now).path, 'login');
  assert.equal(routeGuard.evaluate('login', customer, now).path, 'dashboard');
  assert.equal(routeGuard.evaluate('admin', customer, now).path, 'dashboard');
  assert.equal(routeGuard.evaluate('dashboard', admin, now).path, 'admin');
  assert.equal(routeGuard.evaluate('admin-audit', admin, now).path, 'admin-audit');
  assert.equal(routeGuard.evaluate('transfer', both, now).path, 'transfer');
  assert.equal(routeGuard.evaluate('accounts', { accessToken: 't', expiresAt: 1000, roles: ['CUSTOMER'] }, now).path, 'login');
  assert.deepEqual(routeGuard.navFor(customer).customer.map((item) => item.path), [
    'dashboard', 'accounts', 'transactions', 'beneficiaries', 'transfer',
    'billers', 'bill-payments', 'cards', 'loans', 'profile', 'notifications'
  ]);
  assert.equal(routeGuard.navFor(customer).admin.length, 0);
  assert.equal(routeGuard.navFor(admin).customer.length, 0);
  const paths = routeGuard.routerConfig().map((route) => route.path);
  assert.ok(paths.includes('transfer'));
  assert.ok(paths.includes('password-recovery'));
  assert.ok(paths.includes('admin-audit'));
});

test('formatting masks accounts and keeps the server amount', () => {
  assert.equal(format.maskAccount('001234567890'), '•••• 7890');
  assert.match(format.formatMoney(124580.4, 'INR'), /1,24,580/);
  assert.equal(format.labelize('TOTP_SETUP_REQUIRED'), 'Totp setup required');
  assert.deepEqual(format.asList({ content: [{ id: 1 }] }), [{ id: 1 }]);
});

test('authenticator setup state is memory only', () => {
  authFlow.clear();
  authFlow.setCredentials('asha', 'a-sufficiently-long-password');
  authFlow.setChallengeId('challenge');
  assert.equal(authFlow.hasCredentials(), true);
  authFlow.clearPassword();
  assert.equal(authFlow.getCredentials().password, '');
  assert.equal(authFlow.getChallengeId(), 'challenge');
  authFlow.clear();
  assert.equal(authFlow.getChallengeId(), '');
  assert.equal(authFlow.hasCredentials(), false);
});
