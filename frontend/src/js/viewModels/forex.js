define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/format',
  '../services/ui-support',
  'oj-c/button',
  'oj-c/input-text',
  'oj-c/input-number',
  'oj-c/select-single',
  'oj-c/badge',
  'oj-c/form-layout',
  'oj-c/skeleton'
], function (ko, accUtils, registry, format, ui) {
  var SYMBOLS = { INR: '₹', USD: '$', EUR: '€', GBP: '£' };

  function ForexViewModel() {
    var self = this;
    var generation = 0;
    var timer = null;
    var idempotencyKey = '';

    self.flow = new ui.Flow(['Amount', 'Rate', 'Confirm']);
    self.problem = new ui.Problem();
    self.loading = ko.observable(true);
    self.busy = ko.observable(false);
    self.accounts = ko.observableArray([]);
    self.sourceAccountId = ko.observable(null);
    self.destinationAccountId = ko.observable(null);
    self.amount = ko.observable(null);
    self.quote = ko.observable(null);
    self.challengeId = ko.observable('');
    self.otpCode = ko.observable('');
    self.receipt = ko.observable(null);
    self.history = ko.observableArray([]);
    self.historyLoading = ko.observable(true);
    self.historyError = ko.observable('');
    self.now = ko.observable(Date.now());
    self.sourceMessages = ko.observableArray([]);
    self.destinationMessages = ko.observableArray([]);
    self.amountMessages = ko.observableArray([]);
    self.otpMessages = ko.observableArray([]);

    function byId(id) {
      return self.accounts().filter(function (account) { return account.accountId === id; })[0] || null;
    }

    self.source = ko.pureComputed(function () { return byId(self.sourceAccountId()); });
    self.destination = ko.pureComputed(function () { return byId(self.destinationAccountId()); });

    self.hasWallet = ko.pureComputed(function () {
      return self.accounts().some(function (account) { return (account.currencyCode || 'INR') !== 'INR'; });
    });

    function walletOption(account) {
      var option = ui.accountOption(account);
      if ((account.currencyCode || 'INR') !== 'INR') {
        option.label = account.currencyCode + ' wallet ' + format.maskAccount(account.accountNumber) + ' · ' +
          format.formatMoney(account.availableBalance, account.currencyCode);
      }
      return option;
    }

    self.sourceOptions = ko.pureComputed(function () {
      return ui.options(self.accounts().map(walletOption));
    });

    self.destinationOptions = ko.pureComputed(function () {
      var source = self.source();
      return ui.options(self.accounts().filter(function (account) {
        return !source || account.currencyCode !== source.currencyCode;
      }).map(walletOption));
    });

    function pickDestination() {
      var source = self.source();
      var destination = self.destination();
      if (!source || (destination && destination.currencyCode !== source.currencyCode)) {
        return;
      }
      var other = self.accounts().filter(function (account) { return account.currencyCode !== source.currencyCode; })[0];
      self.destinationAccountId(other ? other.accountId : null);
    }

    self.sourceAccountId.subscribe(pickDestination);

    self.prefix = ko.pureComputed(function () {
      var source = self.source();
      return SYMBOLS[source && source.currencyCode] || '';
    });

    self.secondsLeft = ko.pureComputed(function () {
      var quote = self.quote();
      if (!quote || !quote.expiresAt) {
        return 0;
      }
      return Math.max(0, Math.round((new Date(quote.expiresAt).getTime() - self.now()) / 1000));
    });

    self.expired = ko.pureComputed(function () {
      return !!self.quote() && self.secondsLeft() === 0;
    });

    self.countdown = ko.pureComputed(function () {
      var left = self.secondsLeft();
      return Math.floor(left / 60) + ':' + String(left % 60).padStart(2, '0');
    });

    self.rateLine = ko.pureComputed(function () {
      var quote = self.quote();
      if (!quote) {
        return '';
      }
      return '1 ' + quote.sourceCurrency + ' = ' + Number(quote.exchangeRate).toPrecision(6).replace(/\.?0+$/, '') + ' ' + quote.destinationCurrency;
    });

    self.inverseLine = ko.pureComputed(function () {
      var quote = self.quote();
      if (!quote || !Number(quote.exchangeRate)) {
        return '';
      }
      return '1 ' + quote.destinationCurrency + ' = ' + (1 / Number(quote.exchangeRate)).toFixed(4).replace(/\.?0+$/, '') + ' ' + quote.sourceCurrency;
    });

    self.amountConverter = ui.amountConverter;
    self.label = format.labelize;
    self.money = format.formatMoney;
    self.when = format.formatDateTime;
    self.statusVariant = ui.statusVariant;

    function tick() {
      self.now(Date.now());
    }

    self.openAccounts = function () {
      registry.go('accounts');
      return false;
    };

    self.getRate = function () {
      var source = self.source();
      var errors = {
        source: source ? '' : 'Choose the account to convert from.',
        destination: self.destination() ? '' : 'Choose the account to receive the money.',
        amount: ui.amountError(self.amount(), null, null, source && source.currencyCode)
      };
      if (!errors.amount && source && ui.parseAmount(self.amount()) > Number(source.availableBalance)) {
        errors.amount = 'The amount is more than the available balance.';
      }
      self.sourceMessages(ui.messages(errors.source));
      self.destinationMessages(ui.messages(errors.destination));
      self.amountMessages(ui.messages(errors.amount));
      if (errors.source || errors.destination || errors.amount || self.busy()) {
        return false;
      }
      self.busy(true);
      self.problem.clear();
      registry.forex.quote({
        sourceAccountId: self.sourceAccountId(),
        destinationAccountId: self.destinationAccountId(),
        sourceAmount: ui.parseAmount(self.amount())
      }).then(function (quote) {
        self.quote(quote);
        idempotencyKey = ui.newKey('fx');
        tick();
        self.flow.go(1);
      }).catch(function (error) {
        self.problem.set(error, 'A rate could not be fetched.');
      }).finally(function () {
        self.busy(false);
      });
      return false;
    };

    self.back = function () {
      self.problem.clear();
      self.flow.go(0);
    };

    self.sendCode = function () {
      var quote = self.quote();
      if (!quote || self.busy()) {
        return;
      }
      if (self.expired()) {
        self.problem.show('The rate has expired. Get a new rate to continue.');
        return;
      }
      self.busy(true);
      self.problem.clear();
      registry.otp.requestForexChallenge(quote.quoteId).then(function (challenge) {
        self.challengeId(challenge && challenge.challengeId);
        self.otpCode('');
        self.otpMessages([]);
        self.flow.go(2);
        accUtils.announce('A one-time code has been sent to your registered email and mobile.', 'polite');
      }).catch(function (error) {
        self.problem.set(error, 'The code could not be sent.');
      }).finally(function () {
        self.busy(false);
      });
    };

    self.convert = function () {
      var error = registry.otp.validateCode(self.otpCode());
      self.otpMessages(ui.messages(error));
      if (error || self.busy()) {
        return false;
      }
      self.busy(true);
      self.problem.clear();
      registry.forex.convert({
        quoteId: self.quote().quoteId,
        idempotencyKey: idempotencyKey,
        otpChallengeId: self.challengeId(),
        otpCode: String(self.otpCode()).trim()
      }).then(function (receipt) {
        self.receipt(receipt);
        self.flow.go(3);
        accUtils.announce('Conversion complete.', 'polite');
        loadHistory();
      }).catch(function (failure) {
        self.otpMessages(ui.messages(ui.fieldMessage(failure, 'otpCode')));
        self.problem.set(failure, 'The conversion could not be completed.');
      }).finally(function () {
        self.busy(false);
      });
      return false;
    };

    self.again = function () {
      self.quote(null);
      self.receipt(null);
      self.amount(null);
      self.problem.clear();
      self.flow.go(0);
      loadAccounts(generation);
    };

    function loadHistory() {
      self.historyLoading(true);
      self.historyError('');
      registry.forex.list().then(function (page) {
        self.history(format.asList(page));
      }).catch(function (error) {
        self.historyError((error && error.message) || 'Recent conversions could not be loaded.');
      }).finally(function () {
        self.historyLoading(false);
      });
    }

    function loadAccounts(ticket) {
      self.loading(true);
      return registry.accounts.getAccounts().then(function (accounts) {
        if (ticket !== generation) {
          return;
        }
        var active = format.asList(accounts).filter(function (account) { return account.accountStatus === 'ACTIVE'; });
        self.accounts(active);
        if (!byId(self.sourceAccountId())) {
          var inr = active.filter(function (account) { return (account.currencyCode || 'INR') === 'INR'; })[0];
          self.sourceAccountId(inr ? inr.accountId : (active[0] && active[0].accountId) || null);
        }
        pickDestination();
      }).catch(function (error) {
        if (ticket === generation) {
          self.problem.set(error, 'Your accounts could not be loaded.');
        }
      }).finally(function () {
        if (ticket === generation) {
          self.loading(false);
        }
      });
    }

    self.connected = function () {
      var ticket = ++generation;
      accUtils.announce('Foreign exchange.', 'polite');
      document.title = 'Forex | Internet Banking';
      self.problem.clear();
      timer = window.setInterval(tick, 1000);
      loadAccounts(ticket);
      loadHistory();
    };

    self.disconnected = function () {
      generation += 1;
      window.clearInterval(timer);
      timer = null;
    };
  }

  return ForexViewModel;
});
