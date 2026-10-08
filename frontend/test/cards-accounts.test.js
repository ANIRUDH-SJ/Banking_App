const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ForexService = require('../src/js/services/forex-service');
const pinCrypto = require('../src/js/services/pin-crypto');
const ApiClientService = require('../src/js/services/api-client-service');
const SessionService = require('../src/js/services/session-service');

function loadAmd(name) {
  let service;
  const source = fs.readFileSync(path.join(__dirname, '../src/js/services', name), 'utf8');
  vm.runInNewContext(source, {
    define: (dependencies, factory) => {
      assert.deepEqual(Array.from(dependencies), []);
      service = factory();
    }
  }, { filename: name });
  return service;
}

test('card reveal follows backend availability and returns to a masked number', async () => {
  let CardView;
  let requests = 0;
  const observable = (initial) => {
    let value = initial;
    return function (next) {
      if (arguments.length) value = next;
      return value;
    };
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../src/js/services/card-view.js'), 'utf8'), {
    define: (names, factory) => {
      CardView = factory(
        { observable, pureComputed: (compute) => compute },
        { cards: { reveal: async () => { requests += 1; return { cardNumber: '4242424242424242', revealSeconds: 30 }; } } },
        { labelize: (value) => value, asList: (value) => value }
      );
    },
    window: { setInterval: () => 1, clearInterval: () => {} },
    Date, Math, Number, String
  });

  const unavailable = new CardView({ cardId: 1, lastFour: '4242', cardType: 'DEBIT', cardNetwork: 'VISA', status: 'ACTIVE', revealable: false });
  unavailable.reveal();
  assert.equal(unavailable.canReveal, false);
  assert.equal(requests, 0);
  assert.match(unavailable.display(), /4242$/);

  const available = new CardView({ cardId: 2, lastFour: '4242', cardType: 'DEBIT', cardNetwork: 'VISA', status: 'ACTIVE', revealable: true });
  available.reveal();
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(requests, 1);
  assert.equal(available.display(), '4242 4242 4242 4242');
  available.hide();
  assert.match(available.display(), /^••••/);
});

test('a card PIN is four digits and not a trivial sequence', () => {
  assert.equal(pinCrypto.formatError('12'), 'Enter the four-digit PIN.');
  assert.equal(pinCrypto.formatError('1234'), '');
  assert.match(pinCrypto.strengthError('1111'), /repeated/);
  assert.match(pinCrypto.strengthError('1234'), /sequential/);
  assert.equal(pinCrypto.strengthError('4829'), '');
});

test('a PIN is sealed with the bank key and a freshness timestamp', async () => {
  const imported = [];
  const cards = { pinKey: async () => ({ keyId: 'key-1', publicKey: 'AQID' }) };
  const subtle = {
    importKey: async (format, key, algorithm) => {
      imported.push([format, algorithm]);
      return { format: format };
    },
    encrypt: async (algorithm, key, payload) => {
      imported.push(JSON.parse(Buffer.from(payload).toString()));
      return Uint8Array.from([1, 2, 3, 4]).buffer;
    }
  };
  const sealer = new pinCrypto.PinSealer(cards, subtle, () => 1_700_000_000_000);
  const result = await sealer.seal(['4829', null]);
  assert.equal(result.pinKeyId, 'key-1');
  assert.equal(result.sealed[1], null);
  assert.equal(result.sealed[0], Buffer.from([1, 2, 3, 4]).toString('base64'));
  assert.deepEqual(imported[0], ['spki', { name: 'RSA-OAEP', hash: 'SHA-256' }]);
  assert.deepEqual(imported[1], { pin: '4829', issuedAt: 1_700_000_000_000 });
});

test('card, statement and notice calls use the new routes', async () => {
  const calls = [];
  const api = {
    get: async (path) => { calls.push(['get', path]); return path.endsWith('/unread-count') ? { unread: 4 } : { content: [] }; },
    post: async (path, body) => { calls.push(['post', path, body]); return { status: 'SENT' }; },
    put: async (path, body) => { calls.push(['put', path, body]); return { pinSet: true }; },
    patch: async (path, body) => { calls.push(['patch', path, body]); return { nickname: body && body.nickname }; },
    download: async (path) => { calls.push(['download', path]); return { blob: 'pdf', filename: 'statement.pdf' }; }
  };
  const CardService = loadAmd('card-service.js');
  const NotificationService = loadAmd('notification-service.js');
  const AccountService = loadAmd('account-service.js');
  const cards = new CardService(api);
  const notices = new NotificationService(api);
  const accounts = new AccountService(api);
  await cards.reveal(7);
  await cards.setPin(7, { pinKeyId: 'k', encryptedPin: 'cipher' });
  await cards.emiOptions(7, 3);
  await cards.convertToEmi(7, 3, 6, 'emi-1');
  assert.equal(await notices.unreadCount(), 4);
  await notices.get('n1');
  await accounts.downloadStatementPdf(9, '?from=2026-01-01');
  await accounts.emailStatement(9, { from: '2026-01-01', type: 'TRANSFER' });
  await accounts.rename(9, 'Household');
  assert.deepEqual(calls.map((call) => call[1]), [
    '/api/v1/cards/7/reveal',
    '/api/v1/cards/7/pin',
    '/api/v1/cards/7/transactions/3/emi-options',
    '/api/v1/cards/7/transactions/3/emi',
    '/api/v1/notifications/unread-count',
    '/api/v1/notifications/n1',
    '/api/v1/accounts/9/statement.pdf?from=2026-01-01',
    '/api/v1/accounts/9/statement-emails',
    '/api/v1/accounts/9'
  ]);
  assert.equal(calls[3][2].tenureMonths, 6);
  assert.equal(calls[7][2].type, 'TRANSFER');
  assert.equal(calls[8][2].nickname, 'Household');
});

test('the forex converter asks for an indicative rate', async () => {
  const calls = [];
  const forex = new ForexService({
    get: async (path) => { calls.push(path); return { convertedAmount: 1491 }; }
  });
  assert.equal((await forex.preview('USD', 'JPY', 10)).convertedAmount, 1491);
  assert.equal(calls[0], '/api/v1/forex/rates/convert?from=USD&to=JPY&amount=10');
  await forex.rates();
  assert.equal(calls[1], '/api/v1/forex/rates');
});

test('statement downloads keep the file bytes and the server filename', async () => {
  const session = new SessionService(new MapStorage(), () => 1_000);
  const exp = 9_000_000_000;
  const token = Buffer.from(JSON.stringify({ alg: 'none' })).toString('base64url') + '.' +
    Buffer.from(JSON.stringify({ exp: exp })).toString('base64url') + '.sig';
  session.save({ accessToken: token, tokenType: 'Bearer' });
  const blob = { size: 4 };
  const client = new ApiClientService({
    session: session,
    fetch: async () => ({
      ok: true,
      status: 200,
      headers: { get: (name) => ({ 'content-type': 'application/pdf', 'content-disposition': 'attachment; filename="statement-7731.pdf"' }[name.toLowerCase()] || '') },
      blob: async () => blob
    })
  });
  const file = await client.download('/api/v1/accounts/9/statement.pdf');
  assert.equal(file.blob, blob);
  assert.equal(file.filename, 'statement-7731.pdf');
});

function MapStorage() {
  const map = new Map();
  this.getItem = (key) => (map.has(key) ? map.get(key) : null);
  this.setItem = (key, value) => map.set(key, String(value));
  this.removeItem = (key) => map.delete(key);
}
