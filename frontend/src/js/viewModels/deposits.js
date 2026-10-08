define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/format',
  '../services/ui-support',
  'oj-c/button',
  'oj-c/input-number',
  'oj-c/input-text',
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
    var closureKey = '';
    var closureTicket = 0;

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
    self.closingDeposit = ko.observable(null);
    self.closureQuote = ko.observable(null);
    self.closureChallengeId = ko.observable('');
    self.closureCode = ko.observable('');
    self.closureCodeMessages = ko.observableArray([]);
    self.closureBusy = ko.observable(false);
    self.closureError = new ui.Problem();

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

    self.closureExpiry = ko.pureComputed(function () {
      var quote = self.closureQuote();
      return quote && quote.expiresAt ? format.formatTime(new Date(quote.expiresAt).getTime()) : '';
    });

    self.progress = function (deposit) {
      var term = Number(deposit.termMonths) || 0;
      return term ? Math.min(100, Math.round((Number(deposit.installmentsPaid) || 0) / term * 100)) : 0;
    };

    self.sourceAccount = function (deposit) {
      var account = self.accounts().find(function (item) { return item.accountId === deposit.sourceAccountId; });
      return account ? (account.accountType || 'Account') + ' ···· ' + String(account.accountNumber || '').slice(-4) : 'Source account';
    };

    self.canPayInstallment = function (deposit) {
      if (deposit.status !== 'ACTIVE' || deposit.kind !== 'RD' || !deposit.nextDueAt) return false;
      var utc = /(?:Z|[+-]\d\d:\d\d)$/.test(deposit.nextDueAt) ? deposit.nextDueAt : deposit.nextDueAt + 'Z';
      return new Date(utc).getTime() <= Date.now();
    };

    self.startClosure = function (deposit) {
      if (self.closureBusy() || deposit.status !== 'ACTIVE') return;
      var ticket = ++closureTicket;
      self.closingDeposit(deposit);
      self.closureQuote(null);
      self.closureChallengeId('');
      self.closureCode('');
      self.closureError.clear();
      self.closureBusy(true);
      registry.deposits.closureQuote(deposit.depositId).then(function (quote) {
        if (ticket !== closureTicket) return;
        self.closureQuote(quote);
        closureKey = ui.newKey('deposit-close');
      }).catch(function (error) {
        if (ticket === closureTicket) self.closureError.set(error, 'An early-closure quote could not be prepared.');
      }).finally(function () {
        if (ticket === closureTicket) self.closureBusy(false);
      });
    };

    self.cancelClosure = function () {
      if (self.closureBusy()) return;
      closureTicket += 1;
      self.closingDeposit(null);
      self.closureQuote(null);
      self.closureChallengeId('');
      self.closureCode('');
      self.closureError.clear();
      closureKey = '';
    };

    self.sendClosureCode = function () {
      var deposit = self.closingDeposit();
      var quote = self.closureQuote();
      if (!deposit || !quote || self.closureBusy()) return;
      if (new Date(quote.expiresAt).getTime() <= Date.now()) {
        self.closureError.show('This closure quote has expired. Cancel and request a new quote.');
        return;
      }
      self.closureBusy(true);
      self.closureError.clear();
      registry.deposits.closureChallenge(deposit.depositId, quote.quoteId).then(function (challenge) {
        self.closureChallengeId(challenge.challengeId);
        self.closureCode('');
        accUtils.announce('A one-time code has been sent.', 'polite');
      }).catch(function (error) {
        self.closureError.set(error, 'A one-time code could not be sent.');
      }).finally(function () {
        self.closureBusy(false);
      });
    };

    self.confirmClosure = function () {
      var deposit = self.closingDeposit();
      var quote = self.closureQuote();
      if (!deposit || !quote || !self.closureChallengeId() || self.closureBusy()) return;
      var error = registry.otp.validateCode(self.closureCode());
      self.closureCodeMessages(ui.messages(error));
      if (error) return;
      self.closureBusy(true);
      self.closureError.clear();
      registry.deposits.close(deposit.depositId, {
        quoteId: quote.quoteId,
        idempotencyKey: closureKey,
        otpChallengeId: self.closureChallengeId(),
        otpCode: String(self.closureCode()).trim()
      }).then(function (receipt) {
        self.deposits.replace(deposit, Object.assign({}, deposit, receipt));
        self.success(self.kindName(deposit.kind) + ' closed. ' + format.formatMoney(receipt.payoutAmount, 'INR') + ' was credited to the source account.');
        self.closureBusy(false);
        self.cancelClosure();
        accUtils.announce('Deposit closed.', 'polite');
      }).catch(function (failure) {
        self.closureCodeMessages(ui.messages(ui.fieldMessage(failure, 'otpCode')));
        self.closureError.set(failure, 'The closure could not be confirmed. You may retry with the same quote and code.');
        self.closureBusy(false);
      });
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
      document.title = 'FD & RD | ORACLE INTERNATIONAL BANK (OIB)';
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
      closureTicket += 1;
    };
  }

  return DepositsViewModel;
});
