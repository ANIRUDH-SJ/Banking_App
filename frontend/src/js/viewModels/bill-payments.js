define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/format',
  '../services/ui-support',
  '../services/BillerService',
  '../services/BillPaymentService',
  '../services/pin-authorization',
  'oj-c/button',
  'oj-c/input-text',
  'oj-c/input-sensitive-text',
  'oj-c/input-number',
  'oj-c/select-single',
  'oj-c/buttonset-single',
  'oj-c/badge',
  'oj-c/form-layout',
  'oj-c/skeleton'
], function (ko, accUtils, registry, format, ui, billers, billPayments, PinAuthorization) {
  var GLYPHS = { ELECTRICITY: 'i-bolt', WATER: 'i-drop', MOBILE: 'i-phone', GAS: 'i-bolt' };

  function BillPaymentsViewModel() {
    var self = this;
    var generation = 0;
    var idempotencyKey = '';

    self.flow = new ui.Flow(['Biller', 'Bill details', 'Review', 'Confirm']);
    self.problem = new ui.Problem();
    self.loading = ko.observable(true);
    self.busy = ko.observable(false);
    self.billers = ko.observableArray([]);
    self.accounts = ko.observableArray([]);
    self.category = ko.observable('ALL');
    self.biller = ko.observable(null);
    self.sourceAccountId = ko.observable(null);
    self.billReference = ko.observable('');
    self.amount = ko.observable(null);
    self.otpCode = ko.observable('');
    self.challengeId = ko.observable('');
    self.receipt = ko.observable(null);
    self.history = ko.observableArray([]);
    self.historyLoading = ko.observable(true);
    self.historyError = ko.observable('');
    self.accountMessages = ko.observableArray([]);
    self.referenceMessages = ko.observableArray([]);
    self.amountMessages = ko.observableArray([]);
    self.otpMessages = ko.observableArray([]);
    self.pinAuth = new PinAuthorization(self.sourceAccountId);

    self.categories = ko.pureComputed(function () {
      var seen = {};
      var items = [{ label: 'All', value: 'ALL' }];
      self.billers().forEach(function (biller) {
        if (biller.category && !seen[biller.category]) {
          seen[biller.category] = true;
          items.push({ label: format.labelize(biller.category), value: biller.category });
        }
      });
      return items;
    });

    self.visibleBillers = ko.pureComputed(function () {
      var category = self.category();
      return self.billers().filter(function (biller) {
        return category === 'ALL' || biller.category === category;
      });
    });

    self.accountOptions = ko.pureComputed(function () {
      return ui.options(self.accounts().map(ui.accountOption));
    });

    self.sourceAccount = ko.pureComputed(function () {
      var id = self.sourceAccountId();
      return self.accounts().filter(function (account) { return account.accountId === id; })[0] || null;
    });

    self.referenceLabel = ko.pureComputed(function () {
      return (self.biller() && self.biller().referenceLabel) || 'Bill reference';
    });

    self.referenceHint = ko.pureComputed(function () {
      return (self.biller() && self.biller().referenceHint) || '';
    });

    self.limitText = ko.pureComputed(function () {
      return limitText(self.biller());
    });

    self.amountText = ko.pureComputed(function () {
      return format.formatMoney(ui.parseAmount(self.amount()), 'INR');
    });

    self.glyph = function (biller) {
      return GLYPHS[String(biller && biller.code || '').toUpperCase()] || 'i-billers';
    };

    self.limitFor = limitText;
    self.amountConverter = ui.amountConverter;
    self.label = format.labelize;
    self.money = format.formatMoney;
    self.when = format.formatDateTime;
    self.mask = format.maskAccount;
    self.statusVariant = ui.statusVariant;

    self.sourceName = ko.pureComputed(function () {
      var account = self.sourceAccount();
      return account ? ui.accountName(account) + ' ' + format.maskAccount(account.accountNumber) : '';
    });

    self.paymentName = function (payment) {
      return (payment && payment.billerName) || self.billerName(payment && payment.billerId);
    };

    self.billerName = function (billerId) {
      var match = self.billers().filter(function (biller) { return biller.billerId === billerId; })[0];
      return match ? match.name : 'Biller';
    };

    self.historyGlyph = function (billerId) {
      var match = self.billers().filter(function (biller) { return biller.billerId === billerId; })[0];
      return self.glyph(match);
    };

    self.choose = function (biller) {
      self.biller(biller);
      self.billReference('');
      self.amount(null);
      self.referenceMessages([]);
      self.amountMessages([]);
      self.problem.clear();
      self.flow.go(1);
    };

    self.changeBiller = function () {
      self.problem.clear();
      self.flow.go(0);
      return false;
    };

    self.toReview = function () {
      var biller = self.biller();
      var reference = String(self.billReference() || '').trim();
      var pattern = biller && biller.referencePattern;
      var referenceError = reference ? '' : 'Enter the ' + self.referenceLabel().toLowerCase() + '.';
      if (!referenceError && pattern && !(new RegExp(pattern)).test(reference)) {
        referenceError = biller.referenceHint || 'Check the ' + self.referenceLabel().toLowerCase() + ' format.';
      }
      var errors = {
        account: self.sourceAccountId() ? '' : 'Choose the account to pay from.',
        reference: referenceError,
        amount: ui.amountError(self.amount(), biller && biller.minAmount, biller && biller.maxAmount, 'INR')
      };
      var account = self.sourceAccount();
      if (!errors.amount && account && ui.parseAmount(self.amount()) > Number(account.availableBalance)) {
        errors.amount = 'The amount is more than the available balance.';
      }
      self.accountMessages(ui.messages(errors.account));
      self.referenceMessages(ui.messages(errors.reference));
      self.amountMessages(ui.messages(errors.amount));
      if (errors.account || errors.reference || errors.amount) {
        self.problem.show('Check the highlighted fields.');
        return false;
      }
      self.problem.clear();
      idempotencyKey = ui.newKey('bill');
      self.flow.go(2);
      return false;
    };

    self.back = function () {
      self.problem.clear();
      self.flow.go(Math.max(0, self.flow.at() - 1));
    };

    function payload() {
      return {
        sourceAccountId: self.sourceAccountId(),
        billerId: self.biller().billerId,
        billReference: String(self.billReference()).trim(),
        amount: ui.parseAmount(self.amount())
      };
    }

    self.usePin = function () {
      self.problem.clear();
      self.pinAuth.use('PIN');
      self.flow.go(3);
    };

    self.sendCode = function () {
      if (self.busy()) {
        return;
      }
      self.pinAuth.use('OTP');
      self.busy(true);
      self.problem.clear();
      billPayments.requestOtp(payload()).then(function (challenge) {
        self.challengeId(challenge && challenge.challengeId);
        self.otpCode('');
        self.otpMessages([]);
        self.flow.go(3);
        accUtils.announce('A one-time code has been sent to your registered email and mobile.', 'polite');
      }).catch(function (error) {
        self.problem.set(error, 'The code could not be sent.');
      }).finally(function () {
        self.busy(false);
      });
    };

    self.pay = function () {
      var usingPin = self.pinAuth.usingPin();
      var error = usingPin ? '' : registry.otp.validateCode(self.otpCode());
      self.otpMessages(ui.messages(error));
      if (error || (usingPin && !self.pinAuth.validate()) || self.busy()) {
        return false;
      }
      self.busy(true);
      self.problem.clear();
      var request = payload();
      request.idempotencyKey = idempotencyKey;
      var authorized = usingPin
        ? self.pinAuth.authorization().then(function (cardPin) { request.cardPin = cardPin; return request; })
        : Promise.resolve(Object.assign(request, { otpChallengeId: self.challengeId(), otpCode: String(self.otpCode()).trim() }));
      authorized.then(billPayments.submitPayment).then(function (receipt) {
        self.receipt(receipt);
        self.pinAuth.reset();
        self.flow.go(4);
        accUtils.announce('Bill payment submitted.', 'polite');
        loadHistory();
        registry.events.emit('accounts-changed');
      }).catch(function (failure) {
        if (usingPin) {
          self.pinAuth.rejected(failure);
          if (!failure || String(failure.code || '').indexOf('PIN_') !== 0) {
            self.problem.set(failure, 'The payment could not be completed.');
          }
        } else {
          self.otpMessages(ui.messages(ui.fieldMessage(failure, 'otpCode')));
          self.problem.set(failure, 'The payment could not be completed.');
        }
      }).finally(function () {
        self.busy(false);
      });
      return false;
    };

    self.again = function () {
      self.receipt(null);
      self.biller(null);
      self.billReference('');
      self.amount(null);
      self.otpCode('');
      self.pinAuth.reset();
      self.problem.clear();
      self.flow.go(0);
    };

    self.openStatement = function () {
      if (self.sourceAccountId()) {
        ui.hand('transactions.accountId', self.sourceAccountId());
      }
      registry.go('transactions');
    };

    self.receiptTitle = ko.pureComputed(function () {
      var status = String(self.receipt() && self.receipt().status || '').toUpperCase();
      if (status === 'COMPLETED') {
        return 'Bill paid';
      }
      if (status === 'REVERSED' || status === 'FAILED') {
        return 'Payment not completed';
      }
      return 'Payment submitted';
    });

    self.receiptPending = ko.pureComputed(function () {
      return String(self.receipt() && self.receipt().status || '').toUpperCase() !== 'COMPLETED';
    });

    function limitText(biller) {
      if (!biller || (biller.minAmount == null && biller.maxAmount == null)) {
        return '';
      }
      if (biller.minAmount != null && biller.maxAmount != null) {
        return format.formatMoney(biller.minAmount, 'INR') + ' to ' + format.formatMoney(biller.maxAmount, 'INR');
      }
      return biller.minAmount != null
        ? 'From ' + format.formatMoney(biller.minAmount, 'INR')
        : 'Up to ' + format.formatMoney(biller.maxAmount, 'INR');
    }

    function loadHistory() {
      self.historyLoading(true);
      self.historyError('');
      billPayments.listRecent(0, 6).then(function (page) {
        self.history(format.asList(page));
      }).catch(function (error) {
        self.historyError((error && error.message) || 'Recent payments could not be loaded.');
      }).finally(function () {
        self.historyLoading(false);
      });
    }

    self.connected = function () {
      var ticket = ++generation;
      accUtils.announce('Bill payments.', 'polite');
      document.title = 'Bill payments | ORACLE INTERNATIONAL BANK (OIB)';
      self.loading(true);
      self.problem.clear();
      var preset = ui.take('bill.billerId');
      self.pinAuth.load();
      Promise.all([billers.listActive(), registry.accounts.getAccounts()]).then(function (results) {
        if (ticket !== generation) {
          return;
        }
        self.billers(format.asList(results[0]));
        var picked = self.billers().filter(function (biller) { return biller.billerId === preset; })[0];
        if (picked) {
          self.choose(picked);
        }
        var payable = format.asList(results[1]).filter(function (account) {
          return account.accountStatus === 'ACTIVE' && (account.currencyCode || 'INR') === 'INR';
        });
        self.accounts(payable);
        if (!self.sourceAccountId() && payable.length) {
          self.sourceAccountId(payable[0].accountId);
        }
      }).catch(function (error) {
        if (ticket === generation) {
          self.problem.set(error, 'Billers could not be loaded.');
        }
      }).finally(function () {
        if (ticket === generation) {
          self.loading(false);
        }
      });
      loadHistory();
    };

    self.disconnected = function () {
      generation += 1;
    };
  }

  return BillPaymentsViewModel;
});
