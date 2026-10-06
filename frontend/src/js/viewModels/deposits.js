define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/format',
  '../services/ui-support',
  'oj-c/button',
  'oj-c/input-number',
  'oj-c/select-single',
  'oj-c/buttonset-single',
  'oj-c/badge',
  'oj-c/form-layout',
  'oj-c/skeleton'
], function (ko, accUtils, registry, format, ui) {
  var LIMITS = { FD: 1000, RD: 100 };
  var MAXIMUM = 10000000;

  function DepositsViewModel() {
    var self = this;
    var generation = 0;
    var idempotencyKey = '';

    self.flow = new ui.Flow(['Plan', 'Quote']);
    self.problem = new ui.Problem();
    self.success = ko.observable('');
    self.loading = ko.observable(true);
    self.busy = ko.observable(false);
    self.payingId = ko.observable(null);
    self.accounts = ko.observableArray([]);
    self.deposits = ko.observableArray([]);
    self.kind = ko.observable('FD');
    self.sourceAccountId = ko.observable(null);
    self.amount = ko.observable(null);
    self.termMonths = ko.observable(12);
    self.quote = ko.observable(null);
    self.opened = ko.observable(null);
    self.accountMessages = ko.observableArray([]);
    self.amountMessages = ko.observableArray([]);

    self.kindItems = [
      { value: 'FD', label: 'Fixed deposit' },
      { value: 'RD', label: 'Recurring deposit' }
    ];
    self.termItems = [6, 12, 24, 36].map(function (months) {
      return { value: months, label: String(months) };
    });

    self.accountOptions = ko.pureComputed(function () {
      return ui.options(self.accounts().map(ui.accountOption));
    });

    self.amountLabel = ko.pureComputed(function () {
      return self.kind() === 'RD' ? 'Monthly instalment' : 'Deposit amount';
    });

    self.amountHint = ko.pureComputed(function () {
      return 'Minimum ' + format.formatMoney(LIMITS[self.kind()], 'INR') + '.' +
        (self.kind() === 'RD' ? ' The first instalment is taken when the deposit opens.' : ' The full amount is taken when the deposit opens.');
    });

    self.kind.subscribe(function () {
      self.amountMessages([]);
    });

    self.amountConverter = ui.amountConverter;
    self.label = format.labelize;
    self.money = format.formatMoney;
    self.date = format.formatDate;
    self.statusVariant = ui.statusVariant;

    self.kindName = function (kind) {
      return kind === 'RD' ? 'Recurring deposit' : 'Fixed deposit';
    };

    self.expires = ko.pureComputed(function () {
      var quote = self.quote();
      return quote && quote.expiresAt ? format.formatTime(new Date(quote.expiresAt).getTime()) : '';
    });

    self.progress = function (deposit) {
      var term = Number(deposit.termMonths) || 0;
      return term ? Math.min(100, Math.round((Number(deposit.installmentsPaid) || 0) / term * 100)) : 0;
    };

    self.getQuote = function () {
      var errors = {
        account: self.sourceAccountId() ? '' : 'Choose the account to fund the deposit.',
        amount: ui.amountError(self.amount(), LIMITS[self.kind()], MAXIMUM, 'INR')
      };
      self.accountMessages(ui.messages(errors.account));
      self.amountMessages(ui.messages(errors.amount));
      if (errors.account || errors.amount || self.busy()) {
        return false;
      }
      self.busy(true);
      self.problem.clear();
      registry.deposits.quote({
        sourceAccountId: self.sourceAccountId(),
        kind: self.kind(),
        amount: ui.parseAmount(self.amount()),
        termMonths: Number(self.termMonths())
      }).then(function (quote) {
        self.quote(quote);
        idempotencyKey = ui.newKey('deposit');
        self.flow.go(1);
      }).catch(function (error) {
        self.amountMessages(ui.messages(ui.fieldMessage(error, 'amount')));
        self.problem.set(error, 'A quote could not be prepared.');
      }).finally(function () {
        self.busy(false);
      });
      return false;
    };

    self.change = function () {
      self.problem.clear();
      self.flow.go(0);
    };

    self.open = function () {
      var quote = self.quote();
      if (!quote || self.busy()) {
        return;
      }
      if (quote.expiresAt && new Date(quote.expiresAt).getTime() < Date.now()) {
        self.problem.show('This quote has expired. Get a new quote to continue.');
        return;
      }
      self.busy(true);
      self.problem.clear();
      registry.deposits.open(quote.quoteId, idempotencyKey).then(function (deposit) {
        self.opened(deposit);
        self.deposits.unshift(deposit);
        self.success(self.kindName(deposit.kind) + ' opened. ' + format.formatMoney(deposit.installmentOrPrincipal, 'INR') + ' was taken from your account.');
        self.quote(null);
        self.amount(null);
        self.flow.go(0);
        accUtils.announce('Deposit opened.', 'polite');
      }).catch(function (error) {
        self.problem.set(error, 'The deposit could not be opened.');
      }).finally(function () {
        self.busy(false);
      });
    };

    self.payInstallment = function (deposit) {
      if (self.payingId()) {
        return;
      }
      self.payingId(deposit.depositId);
      self.problem.clear();
      self.success('');
      registry.deposits.collectInstallment(deposit.depositId).then(function (updated) {
        self.deposits.replace(deposit, updated || deposit);
        self.success('Instalment of ' + format.formatMoney(deposit.installmentOrPrincipal, 'INR') + ' paid.');
        accUtils.announce('Instalment paid.', 'polite');
      }).catch(function (error) {
        self.problem.set(error, 'The instalment could not be paid.');
      }).finally(function () {
        self.payingId(null);
      });
    };

    self.connected = function () {
      var ticket = ++generation;
      accUtils.announce('Fixed and recurring deposits.', 'polite');
      document.title = 'FD & RD | Internet Banking';
      self.loading(true);
      self.problem.clear();
      self.success('');
      Promise.all([registry.accounts.getAccounts(), registry.deposits.list()]).then(function (results) {
        if (ticket !== generation) {
          return;
        }
        var funding = format.asList(results[0]).filter(function (account) {
          return account.accountStatus === 'ACTIVE' && (account.currencyCode || 'INR') === 'INR';
        });
        self.accounts(funding);
        if (!self.sourceAccountId() && funding.length) {
          self.sourceAccountId(funding[0].accountId);
        }
        self.deposits(format.asList(results[1]));
      }).catch(function (error) {
        if (ticket === generation) {
          self.problem.set(error, 'Your deposits could not be loaded.');
        }
      }).finally(function () {
        if (ticket === generation) {
          self.loading(false);
        }
      });
    };

    self.disconnected = function () {
      generation += 1;
    };
  }

  return DepositsViewModel;
});
