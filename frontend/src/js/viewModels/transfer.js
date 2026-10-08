define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/format',
  '../services/ui-support',
  '../services/beneficiary-service',
  '../services/fund-transfer-service',
  '../services/pin-authorization',
  'oj-c/button',
  'oj-c/input-text',
  'oj-c/input-sensitive-text',
  'oj-c/input-number',
  'oj-c/select-single',
  'oj-c/badge',
  'oj-c/form-layout',
  'oj-c/skeleton'
], function (ko, accUtils, registry, format, ui, BeneficiaryService, FundTransferService, PinAuthorization) {
  var STEPS = ['details', 'review', 'otp', 'receipt'];

  function TransferViewModel() {
    var self = this;
    var payees = new BeneficiaryService(registry.apiClient);
    var transfers = new FundTransferService(registry.apiClient);
    var generation = 0;
    var idempotencyKey = '';

    self.flow = new ui.Flow(['Details', 'Review', 'Confirm']);
    self.problem = new ui.Problem();
    self.loading = ko.observable(true);
    self.busy = ko.observable(false);
    self.accounts = ko.observableArray([]);
    self.beneficiaries = ko.observableArray([]);
    self.sourceAccountId = ko.observable(null);
    self.beneficiaryId = ko.observable(null);
    self.amount = ko.observable(null);
    self.narration = ko.observable('');
    self.otpCode = ko.observable('');
    self.otpChallengeId = ko.observable('');
    self.receipt = ko.observable(null);
    self.history = ko.observableArray([]);
    self.historyLoading = ko.observable(true);
    self.historyError = ko.observable('');
    self.accountMessages = ko.observableArray([]);
    self.beneficiaryMessages = ko.observableArray([]);
    self.amountMessages = ko.observableArray([]);
    self.narrationMessages = ko.observableArray([]);
    self.otpMessages = ko.observableArray([]);
    self.pinAuth = new PinAuthorization(self.sourceAccountId);

    self.step = ko.pureComputed(function () {
      return STEPS[self.flow.at()];
    });

    self.accountOptions = ko.pureComputed(function () {
      return ui.options(self.accounts().map(ui.accountOption));
    });

    self.beneficiaryOptions = ko.pureComputed(function () {
      return ui.options(self.beneficiaries().map(function (payee) {
        return {
          value: payee.beneficiaryId,
          label: payee.nickname + ' · ' + [payee.bankName, payee.maskedAccountNumber].filter(Boolean).join(' ')
        };
      }));
    });

    self.sourceAccount = ko.pureComputed(function () {
      var id = self.sourceAccountId();
      return self.accounts().filter(function (account) { return account.accountId === id; })[0] || null;
    });

    self.beneficiary = ko.pureComputed(function () {
      var id = self.beneficiaryId();
      return self.beneficiaries().filter(function (payee) { return payee.beneficiaryId === id; })[0] || null;
    });

    self.amountText = ko.pureComputed(function () {
      return format.formatMoney(ui.parseAmount(self.amount()), 'INR');
    });

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

    self.payeeName = function (payment) {
      if (payment && payment.beneficiaryNickname) {
        return payment.beneficiaryNickname;
      }
      var id = payment && payment.beneficiaryId;
      var match = self.beneficiaries().filter(function (payee) { return payee.beneficiaryId === id; })[0];
      return match ? match.nickname : 'Beneficiary';
    };

    self.reviewTransfer = function () {
      var errors = {
        account: self.sourceAccountId() ? '' : 'Choose the account to pay from.',
        beneficiary: self.beneficiaryId() ? '' : 'Choose who to pay.',
        amount: ui.amountError(self.amount(), 1, null, 'INR'),
        narration: String(self.narration() || '').length > 500 ? 'Keep the note under 500 characters.' : ''
      };
      var account = self.sourceAccount();
      if (!errors.amount && account && ui.parseAmount(self.amount()) > Number(account.availableBalance)) {
        errors.amount = 'The amount is more than the available balance.';
      }
      self.accountMessages(ui.messages(errors.account));
      self.beneficiaryMessages(ui.messages(errors.beneficiary));
      self.amountMessages(ui.messages(errors.amount));
      self.narrationMessages(ui.messages(errors.narration));
      if (errors.account || errors.beneficiary || errors.amount || errors.narration) {
        self.problem.show('Check the highlighted fields.');
        return false;
      }
      self.problem.clear();
      idempotencyKey = ui.newKey('transfer');
      self.flow.go(1);
      return false;
    };

    self.back = function () {
      self.problem.clear();
      self.flow.go(Math.max(0, self.flow.at() - 1));
    };

    self.usePin = function () {
      self.problem.clear();
      self.pinAuth.use('PIN');
      self.flow.go(2);
    };

    self.useOtp = function () {
      self.pinAuth.use('OTP');
      self.requestOtp();
    };

    self.requestOtp = function () {
      if (self.busy()) {
        return;
      }
      self.pinAuth.use('OTP');
      self.busy(true);
      self.problem.clear();
      registry.otp.requestTransferChallenge(self.sourceAccountId(), self.beneficiaryId(), ui.parseAmount(self.amount())).then(function (challenge) {
        self.otpChallengeId(challenge && challenge.challengeId);
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

    function submit(authorization) {
      return transfers.transfer(Object.assign({
        sourceAccountId: self.sourceAccountId(),
        beneficiaryId: self.beneficiaryId(),
        amount: ui.parseAmount(self.amount()),
        narration: String(self.narration() || '').trim() || null,
        idempotencyKey: idempotencyKey
      }, authorization));
    }

    self.confirmTransfer = function () {
      var usingPin = self.pinAuth.usingPin();
      var error = usingPin ? '' : registry.otp.validateCode(self.otpCode());
      self.otpMessages(ui.messages(error));
      if (error || (usingPin && !self.pinAuth.validate()) || self.busy()) {
        return false;
      }
      self.busy(true);
      self.problem.clear();
      var authorization = usingPin
        ? self.pinAuth.authorization().then(function (cardPin) { return { cardPin: cardPin }; })
        : Promise.resolve({ otpChallengeId: self.otpChallengeId(), otpCode: String(self.otpCode()).trim() });
      authorization.then(submit).then(function (receipt) {
        self.receipt(receipt);
        self.pinAuth.reset();
        self.flow.go(3);
        accUtils.announce('Transfer submitted.', 'polite');
        loadHistory();
      }).catch(function (failure) {
        if (usingPin) {
          self.pinAuth.rejected(failure);
          if (!failure || String(failure.code || '').indexOf('PIN_') !== 0) {
            self.problem.set(failure, 'The transfer could not be completed.');
          }
        } else {
          self.otpMessages(ui.messages(ui.fieldMessage(failure, 'otpCode')));
          self.problem.set(failure, 'The transfer could not be completed.');
        }
      }).finally(function () {
        self.busy(false);
      });
      return false;
    };

    self.again = function () {
      self.receipt(null);
      self.amount(null);
      self.narration('');
      self.otpCode('');
      self.pinAuth.reset();
      self.problem.clear();
      self.flow.go(0);
    };

    self.openBeneficiaries = function () {
      registry.go('beneficiaries');
      return false;
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
        return 'Money sent';
      }
      return status === 'FAILED' || status === 'REVERSED' ? 'Transfer not completed' : 'Transfer submitted';
    });

    self.receiptPending = ko.pureComputed(function () {
      return String(self.receipt() && self.receipt().status || '').toUpperCase() !== 'COMPLETED';
    });

    function loadHistory() {
      self.historyLoading(true);
      self.historyError('');
      Promise.resolve().then(function () {
        return transfers.history(0, 6);
      }).then(function (page) {
        self.history(format.asList(page));
      }).catch(function (error) {
        self.historyError((error && error.message) || 'Recent transfers could not be loaded.');
      }).finally(function () {
        self.historyLoading(false);
      });
    }

    self.refreshOptions = function () {
      var ticket = ++generation;
      var preset = ui.take('transfer.beneficiaryId');
      var source = ui.take('transfer.sourceAccountId');
      self.loading(true);
      self.problem.clear();
      self.pinAuth.load();
      return Promise.all([registry.accounts.getAccounts(), payees.list()]).then(function (results) {
        if (ticket !== generation) {
          return;
        }
        var payable = format.asList(results[0]).filter(function (account) {
          return account.accountStatus === 'ACTIVE' && (account.currencyCode || 'INR') === 'INR';
        });
        var active = format.asList(results[1]).filter(function (payee) { return payee.status === 'ACTIVE'; });
        self.accounts(payable);
        self.beneficiaries(active);
        var owns = function (id) {
          return payable.some(function (account) { return account.accountId === id; });
        };
        if (owns(source)) {
          self.sourceAccountId(source);
        } else if (!owns(self.sourceAccountId())) {
          self.sourceAccountId(payable.length ? payable[0].accountId : null);
        }
        if (preset) {
          self.beneficiaryId(preset);
        } else if (!self.beneficiaryId() && active.length) {
          self.beneficiaryId(active[0].beneficiaryId);
        }
      }).catch(function (error) {
        if (ticket === generation) {
          self.problem.set(error, 'Your accounts could not be loaded.');
        }
      }).finally(function () {
        if (ticket === generation) {
          self.loading(false);
        }
      });
    };

    self.connected = function () {
      accUtils.announce('Transfer money.', 'polite');
      document.title = 'Transfer | ORACLE INTERNATIONAL BANK (OIB)';
      self.refreshOptions();
      loadHistory();
    };

    self.disconnected = function () {
      generation += 1;
    };
  }

  return TransferViewModel;
});
