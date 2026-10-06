define(['knockout', '../accUtils', '../services/registry', '../services/format', 'oj-c/button', 'oj-c/badge', 'oj-c/skeleton'], function (ko, accUtils, registry, format) {
  function operationKey() { return 'forex-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10); }
  function describe(error, fallback) { var message = (error && error.message) || fallback; return error && error.correlationId ? message + ' Reference ' + error.correlationId + '.' : message; }
  function ForexViewModel() {
    var self = this;
    self.step = ko.observable('form'); self.accounts = ko.observableArray([]); self.history = ko.observableArray([]);
    self.sourceAccountId = ko.observable(''); self.destinationAccountId = ko.observable(''); self.amount = ko.observable('');
    self.quote = ko.observable(null); self.otpCode = ko.observable(''); self.challengeId = ko.observable(''); self.receipt = ko.observable(null);
    self.loading = ko.observable(true); self.submitting = ko.observable(false); self.error = ko.observable(''); self.message = ko.observable('');
    self.money = format.formatMoney; self.date = format.formatDateTime; self.label = format.labelize;
    self.accountLabel = function (account) { return format.labelize(account.accountType || 'Account') + ' · ' + format.maskAccount(account.accountNumber) + ' · ' + self.money(account.availableBalance, account.currencyCode); };
    self.sourceAccounts = ko.pureComputed(function () { return self.accounts().filter(function (account) { return String(account.accountStatus || '').toUpperCase() === 'ACTIVE'; }); });
    self.destinationAccounts = ko.pureComputed(function () { return self.sourceAccounts().filter(function (account) { return String(account.accountId) !== String(self.sourceAccountId()) && String(account.currencyCode || '').toUpperCase() !== self.sourceCurrency(); }); });
    self.sourceCurrency = ko.pureComputed(function () { var row = self.sourceAccounts().find(function (account) { return String(account.accountId) === String(self.sourceAccountId()); }); return row ? row.currencyCode : ''; });
    self.quoteRate = ko.pureComputed(function () { var quote = self.quote(); return quote ? String(quote.exchangeRate) + ' ' + quote.destinationCurrency + ' per ' + quote.sourceCurrency : ''; });
    self.requestQuote = function () {
      var amount = Number(self.amount()); self.error(''); self.message('');
      if (!self.sourceAccountId() || !self.destinationAccountId()) { self.error('Choose different active source and destination currency accounts.'); return false; }
      if (!Number.isFinite(amount) || amount <= 0) { self.error('Enter a conversion amount greater than zero.'); return false; }
      self.submitting(true);
      registry.forex.quote({ sourceAccountId: self.sourceAccountId(), destinationAccountId: self.destinationAccountId(), sourceAmount: amount }).then(function (quote) {
        self.quote(quote); self.step('review'); self.message('This rate expires ' + self.date(quote.expiresAt) + '.');
      }).catch(function (error) { self.error(describe(error, 'We could not prepare an exchange quote.')); }).finally(function () { self.submitting(false); });
      return false;
    };
    self.backToForm = function () { self.step('form'); self.error(''); return false; };
    self.requestOtp = function () {
      var quote = self.quote(); if (!quote) { self.step('form'); return false; }
      self.submitting(true); self.error('');
      registry.otp.requestForexChallenge(quote.quoteId).then(function (challenge) {
        self.challengeId(challenge.challengeId); self.otpCode(''); self.step('otp'); self.message('We sent a one-time code to your registered contact method.');
      }).catch(function (error) { self.error(describe(error, 'We could not send the confirmation code.')); }).finally(function () { self.submitting(false); });
      return false;
    };
    self.confirm = function () {
      var codeError = registry.otp.validateCode(self.otpCode()); if (codeError) { self.error(codeError); return false; }
      self.submitting(true); self.error('');
      registry.forex.convert({ quoteId: self.quote().quoteId, otpChallengeId: self.challengeId(), otpCode: self.otpCode().trim(), idempotencyKey: operationKey() }).then(function (receipt) {
        self.receipt(receipt); self.history.unshift(receipt); self.step('receipt'); self.message('Your currency conversion is complete.');
      }).catch(function (error) { self.error(describe(error, 'We could not complete this conversion.')); }).finally(function () { self.submitting(false); });
      return false;
    };
    self.startAgain = function () { self.step('form'); self.amount(''); self.quote(null); self.receipt(null); self.otpCode(''); self.error(''); self.message(''); return false; };
    self.connected = function () {
      document.title = 'Foreign exchange | Internet Banking'; accUtils.announce('Foreign exchange. Get a rate quote before confirming a conversion.', 'polite'); self.loading(true);
      Promise.all([registry.accounts.getAccounts(), registry.forex.list()]).then(function (values) {
        self.accounts(format.asList(values[0])); self.history(format.asList(values[1]));
        var accounts = self.sourceAccounts(); if (!self.sourceAccountId() && accounts.length) { self.sourceAccountId(accounts[0].accountId); }
      }).catch(function (error) { self.error(describe(error, 'Foreign exchange information could not be loaded.')); }).finally(function () { self.loading(false); });
    };
  }
  return ForexViewModel;
});
