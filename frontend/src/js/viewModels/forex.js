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
  var SYMBOLS = { INR: '₹', USD: '$', EUR: '€', GBP: '£', JPY: '¥', AUD: 'A$', CAD: 'C$', SGD: 'S$' };
  var NAMES = {
    INR: 'Indian rupee', USD: 'US dollar', EUR: 'Euro', GBP: 'Pound sterling',
    JPY: 'Japanese yen', AUD: 'Australian dollar', CAD: 'Canadian dollar', SGD: 'Singapore dollar'
  };

  function trimRate(value, digits) {
    var number = Number(value);
    if (!Number.isFinite(number)) {
      return '—';
    }
    return number.toFixed(digits).replace(/(\.\d*?[1-9])0+$/, '$1').replace(/\.0+$/, '');
  }

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

    self.board = ko.observable(null);
    self.boardError = ko.observable('');
    self.cvFrom = ko.observable('USD');
    self.cvTo = ko.observable('INR');
    self.cvAmount = ko.observable(100);
    self.cvResult = ko.observable(null);
    self.cvBusy = ko.observable(false);
    self.cvError = ko.observable('');
    self.cvMessages = ko.observableArray([]);
    var previewTimer = null;
    var previewTicket = 0;

    self.currencies = ko.pureComputed(function () {
      var board = self.board();
      var listed = board && board.currencies && board.currencies.length ? board.currencies : Object.keys(NAMES).map(function (code) {
        return { code: code, name: NAMES[code] };
      });
      return listed;
    });

    self.currencyOptions = ko.pureComputed(function () {
      return ui.options(self.currencies().map(function (currency) {
        return { value: currency.code, label: currency.code + ' · ' + (currency.name || NAMES[currency.code] || currency.code) };
      }));
    });

    self.cvPrefix = ko.pureComputed(function () {
      return SYMBOLS[self.cvFrom()] || '';
    });

    self.boardRows = ko.pureComputed(function () {
      return self.currencies().filter(function (currency) {
        return currency.code !== 'INR' && Number(currency.inrValue);
      }).map(function (currency) {
        var value = Number(currency.inrValue);
        return {
          code: currency.code,
          name: currency.name || NAMES[currency.code],
          buy: '₹' + trimRate(value, value < 1 ? 4 : 2),
          per100: format.formatMoney(100 / value, currency.code)
        };
      });
    });

    self.cvRateLine = ko.pureComputed(function () {
      var result = self.cvResult();
      return result ? '1 ' + result.fromCurrency + ' = ' + trimRate(result.exchangeRate, 6) + ' ' + result.toCurrency : '';
    });

    self.cvInverseLine = ko.pureComputed(function () {
      var result = self.cvResult();
      return result ? '1 ' + result.toCurrency + ' = ' + trimRate(result.inverseRate, 6) + ' ' + result.fromCurrency : '';
    });

    self.cvSource = ko.pureComputed(function () {
      var result = self.cvResult() || self.board();
      if (!result) {
        return '';
      }
      var source = result.rateSource === 'DEMO_CONFIGURED' ? 'Bank reference rates (demo)' : format.labelize(result.rateSource || 'bank');
      return source + (result.asOf ? ' · as of ' + format.formatDateTime(result.asOf) : '');
    });

    function preview() {
      var from = self.cvFrom();
      var to = self.cvTo();
      var amount = ui.parseAmount(self.cvAmount());
      var error = !Number.isFinite(amount) || amount <= 0 ? 'Enter an amount to convert.' : '';
      if (!error && amount > 1e12) {
        error = 'Enter a smaller amount.';
      }
      self.cvMessages(ui.messages(error));
      if (error || !from || !to) {
        self.cvResult(null);
        return;
      }
      if (from === to) {
        self.cvResult({ fromCurrency: from, toCurrency: to, amount: amount, convertedAmount: amount, exchangeRate: 1, inverseRate: 1,
          rateSource: self.board() && self.board().rateSource, asOf: self.board() && self.board().asOf });
        return;
      }
      var ticket = ++previewTicket;
      self.cvBusy(true);
      self.cvError('');
      registry.forex.preview(from, to, amount).then(function (result) {
        if (ticket === previewTicket) {
          self.cvResult(result);
        }
      }).catch(function (failure) {
        if (ticket === previewTicket) {
          self.cvResult(null);
          self.cvError((failure && failure.message) || 'A rate is not available right now.');
        }
      }).finally(function () {
        if (ticket === previewTicket) {
          self.cvBusy(false);
        }
      });
    }

    function schedulePreview() {
      window.clearTimeout(previewTimer);
      previewTimer = window.setTimeout(preview, 250);
    }

    [self.cvFrom, self.cvTo, self.cvAmount].forEach(function (field) {
      field.subscribe(schedulePreview);
    });

    self.swap = function () {
      var from = self.cvFrom();
      self.cvFrom(self.cvTo());
      self.cvTo(from);
    };

    function loadBoard() {
      self.boardError('');
      return registry.forex.rates().then(function (board) {
        self.board(board);
      }).catch(function (error) {
        self.boardError((error && error.message) || 'Reference rates could not be loaded.');
      }).then(preview);
    }

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
        option.label = ui.accountName(account) + ' ' + format.maskAccount(account.accountNumber) + ' · ' +
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
      loadBoard();
    };

    self.disconnected = function () {
      generation += 1;
      window.clearInterval(timer);
      window.clearTimeout(previewTimer);
      previewTicket += 1;
      timer = null;
    };
  }

  return ForexViewModel;
});
