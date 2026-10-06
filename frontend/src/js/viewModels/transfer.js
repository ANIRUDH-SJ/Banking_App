define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/format',
  '../services/beneficiary-service',
  '../services/fund-transfer-service',
  '../services/member2-style',
  'oj-c/button'
], function (ko, accUtils, registry, format, BeneficiaryService, FundTransferService) {
  function newIdempotencyKey() {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID();
    return 'transfer-' + Date.now() + '-' + Math.random().toString(36).slice(2);
  }

  function TransferViewModel() {
    var self = this;
    var beneficiaries = new BeneficiaryService(registry.apiClient);
    var transfers = new FundTransferService(registry.apiClient);
    var idempotencyKey = newIdempotencyKey();
    self.accounts = ko.observableArray([]);
    self.beneficiaries = ko.observableArray([]);
    self.sourceAccountId = ko.observable('');
    self.beneficiaryId = ko.observable('');
    self.amount = ko.observable('');
    self.narration = ko.observable('');
    self.otpCode = ko.observable('');
    self.otpChallengeId = ko.observable('');
    self.receipt = ko.observable(null);
    self.step = ko.observable('form');
    self.loading = ko.observable(false);
    self.busy = ko.observable(false);
    self.error = ko.observable('');
    self.message = ko.observable('');
    self.money = format.formatMoney;
    self.accountLabel = function (account) {
      return format.labelize(account.accountType) + ' · ' +
        format.maskAccount(account.accountNumber) + ' · ' +
        format.formatMoney(account.availableBalance, account.currencyCode);
    };
    self.beneficiaryLabel = function (beneficiary) {
      return beneficiary.nickname + ' · ' + beneficiary.maskedAccountNumber;
    };
    self.selectedAccountLabel = ko.pureComputed(function () {
      var account = self.accounts().find(function (row) {
        return String(row.accountId) === String(self.sourceAccountId());
      });
      return account ? self.accountLabel(account) : '';
    });
    self.selectedBeneficiaryLabel = ko.pureComputed(function () {
      var beneficiary = self.beneficiaries().find(function (row) {
        return String(row.beneficiaryId) === String(self.beneficiaryId());
      });
      return beneficiary ? self.beneficiaryLabel(beneficiary) : '';
    });
    self.formattedAmount = ko.pureComputed(function () {
      return self.amount() ? format.formatMoney(self.amount(), 'INR') : '—';
    });

    self.refreshOptions = function () {
      self.loading(true);
      self.error('');
      return Promise.all([registry.accounts.getAccounts(), beneficiaries.list()]).then(function (results) {
        var owned = format.asList(results[0]).filter(function (account) {
          return account.accountStatus === 'ACTIVE' && account.currencyCode === 'INR';
        });
        var active = results[1].filter(function (beneficiary) {
          return beneficiary.status === 'ACTIVE';
        });
        self.accounts(owned);
        self.beneficiaries(active);
        if (!owned.some(function (account) { return String(account.accountId) === String(self.sourceAccountId()); })) {
          self.sourceAccountId(owned.length ? owned[0].accountId : '');
        }
        if (!active.some(function (beneficiary) { return String(beneficiary.beneficiaryId) === String(self.beneficiaryId()); })) {
          self.beneficiaryId(active.length ? active[0].beneficiaryId : '');
        }
      }).catch(function (error) {
        self.error(error.message || 'Unable to load transfer options.');
      }).finally(function () { self.loading(false); });
    };

    self.reviewTransfer = function () {
      if (self.busy()) return false;
      var account = self.accounts().find(function (row) {
        return String(row.accountId) === String(self.sourceAccountId());
      });
      var amount = Number(self.amount());
      if (!account || !self.selectedBeneficiaryLabel()) {
        self.error('Choose an active source account and verified beneficiary.');
      } else if (!Number.isFinite(amount) || amount < 0.01 || !/^\d+(\.\d{1,4})?$/.test(String(self.amount()))) {
        self.error('Enter a valid amount greater than zero, with up to four decimal places.');
      } else if (amount > Number(account.availableBalance)) {
        self.error('This amount exceeds the available balance.');
      } else {
        self.error('');
        self.step('review');
      }
      return false;
    };

    self.backToForm = function () {
      self.otpChallengeId('');
      self.otpCode('');
      self.step('form');
    };

    self.requestOtp = function () {
      if (self.busy()) return;
      self.busy(true);
      self.error('');
      registry.otp.requestTransferChallenge(
        Number(self.sourceAccountId()),
        Number(self.beneficiaryId()),
        Number(self.amount())
      ).then(function (challenge) {
        self.otpChallengeId(challenge.challengeId);
        self.otpCode('');
        self.step('otp');
        self.message('Enter the one-time code sent for this transfer.');
      }).catch(function (error) {
        self.error(error.message || 'Unable to request a transfer code.');
      }).finally(function () { self.busy(false); });
    };

    self.confirmTransfer = function () {
      if (self.busy()) return false;
      var validation = registry.otp.validateCode(self.otpCode());
      if (validation) {
        self.error(validation);
        return false;
      }
      self.busy(true);
      self.error('');
      transfers.transfer({
        sourceAccountId: Number(self.sourceAccountId()),
        beneficiaryId: Number(self.beneficiaryId()),
        amount: Number(self.amount()),
        narration: self.narration().trim(),
        idempotencyKey: idempotencyKey,
        otpChallengeId: self.otpChallengeId(),
        otpCode: self.otpCode().trim()
      }).then(function (receipt) {
        self.receipt(receipt);
        self.step('receipt');
        self.message('The bank received your transfer. Keep the reference for your records.');
      }).catch(function (error) {
        self.error(error.message || 'Unable to submit transfer. Check the status before trying again.');
      }).finally(function () { self.busy(false); });
      return false;
    };

    self.startAnotherTransfer = function () {
      idempotencyKey = newIdempotencyKey();
      self.amount('');
      self.narration('');
      self.otpCode('');
      self.otpChallengeId('');
      self.receipt(null);
      self.step('form');
      self.message('');
      self.error('');
      self.refreshOptions();
    };

    self.connected = function () {
      accUtils.announce('Transfer page loaded.', 'polite');
      document.title = 'Transfer | Internet Banking';
      self.refreshOptions();
    };
  }
  return TransferViewModel;
});
