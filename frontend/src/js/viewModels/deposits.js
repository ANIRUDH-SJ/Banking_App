define(['knockout', '../accUtils', '../services/registry', '../services/format', 'oj-c/button', 'oj-c/badge', 'oj-c/skeleton'], function (ko, accUtils, registry, format) {
  function operationKey(prefix) {
    return prefix + '-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10);
  }
  function describe(error, fallback) {
    var message = (error && error.message) || fallback;
    return error && error.correlationId ? message + ' Reference ' + error.correlationId + '.' : message;
  }
  function DepositsViewModel() {
    var self = this;
    self.step = ko.observable('form');
    self.kind = ko.observable('FD');
    self.sourceAccountId = ko.observable('');
    self.amount = ko.observable('');
    self.termMonths = ko.observable('12');
    self.accounts = ko.observableArray([]);
    self.deposits = ko.observableArray([]);
    self.quote = ko.observable(null);
    self.message = ko.observable('');
    self.error = ko.observable('');
    self.loading = ko.observable(true);
    self.submitting = ko.observable(false);
    self.money = format.formatMoney;
    self.date = format.formatDate;
    self.label = format.labelize;
    self.accountLabel = function (account) {
      return format.labelize(account.accountType || 'Account') + ' · ' + format.maskAccount(account.accountNumber) + ' · ' + format.formatMoney(account.availableBalance, account.currencyCode);
    };
    self.eligibleAccounts = ko.pureComputed(function () {
      return self.accounts().filter(function (account) { return String(account.currencyCode || 'INR').toUpperCase() === 'INR' && String(account.accountStatus || '').toUpperCase() === 'ACTIVE'; });
    });
    self.depositName = ko.pureComputed(function () { return self.kind() === 'RD' ? 'Recurring deposit' : 'Fixed deposit'; });
    self.minimumHint = ko.pureComputed(function () { return self.kind() === 'RD' ? 'Minimum monthly installment: ₹100' : 'Minimum opening amount: ₹1,000'; });
    self.canQuote = ko.pureComputed(function () { return self.eligibleAccounts().length > 0 && !self.submitting(); });
    self.selectKind = function (_, event) {
      self.kind(event.currentTarget.getAttribute('data-kind'));
      self.quote(null); self.error(''); self.message('');
      return false;
    };
    self.requestQuote = function () {
      var amount = Number(self.amount());
      var minimum = self.kind() === 'RD' ? 100 : 1000;
      self.error(''); self.message('');
      if (!self.sourceAccountId()) { self.error('Choose the INR account that will fund this deposit.'); return false; }
      if (!Number.isFinite(amount) || amount < minimum || amount > 10000000) { self.error('Enter an amount between ' + self.money(minimum, 'INR') + ' and ₹1,00,00,000.'); return false; }
      self.submitting(true);
      registry.deposits.quote({ sourceAccountId: self.sourceAccountId(), kind: self.kind(), amount: amount, termMonths: Number(self.termMonths()) }).then(function (quote) {
        self.quote(quote); self.step('review'); self.message('Your rate is held until ' + format.formatDateTime(quote.expiresAt) + '.');
      }).catch(function (error) { self.error(describe(error, 'We could not prepare a deposit quote.')); }).finally(function () { self.submitting(false); });
      return false;
    };
    self.backToForm = function () { self.step('form'); self.error(''); return false; };
    self.openDeposit = function () {
      var quote = self.quote();
      if (!quote) { self.step('form'); return false; }
      self.submitting(true); self.error('');
      registry.deposits.open(quote.quoteId, operationKey('deposit')).then(function (deposit) {
        self.deposits.unshift(deposit); self.step('receipt'); self.message('Your ' + self.depositName().toLowerCase() + ' is now open.');
      }).catch(function (error) { self.error(describe(error, 'We could not open this deposit. Your account has not been debited twice.')); }).finally(function () { self.submitting(false); });
      return false;
    };
    self.startAgain = function () { self.quote(null); self.amount(''); self.step('form'); self.error(''); self.message(''); return false; };
    self.connected = function () {
      document.title = 'Fixed & recurring deposits | Internet Banking';
      accUtils.announce('Fixed and recurring deposits. Choose an account to view a deposit quote.', 'polite');
      self.loading(true);
      Promise.all([registry.accounts.getAccounts(), registry.deposits.list()]).then(function (values) {
        self.accounts(format.asList(values[0])); self.deposits(format.asList(values[1]));
        if (!self.sourceAccountId() && self.eligibleAccounts().length) { self.sourceAccountId(self.eligibleAccounts()[0].accountId); }
      }).catch(function (error) { self.error(describe(error, 'Deposit information could not be loaded.')); }).finally(function () { self.loading(false); });
    };
  }
  return DepositsViewModel;
});
