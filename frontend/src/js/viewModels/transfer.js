define(['knockout', '../accUtils', 'ojs/ojbutton'], function (ko, accUtils) {
  function TransferViewModel() {
    var self = this;
    self.step = ko.observable('form');
    self.message = ko.observable('Enter transfer details to continue.');
    self.accounts = ko.observableArray([
      { accountId: '1', label: 'Savings Account · •••• 1001 · ₹ 25,000.00' },
      { accountId: '2', label: 'Current Account · •••• 2001 · ₹ 10,500.00' }
    ]);
    self.beneficiaries = ko.observableArray([
      { beneficiaryId: '1', label: 'Anita Kumar · •••• 8821' },
      { beneficiaryId: '2', label: 'Rahul Kumar · •••• 4118' }
    ]);
    self.sourceAccountId = ko.observable('1');
    self.beneficiaryId = ko.observable('1');
    self.amount = ko.observable('');
    self.narration = ko.observable('');
    self.otpCode = ko.observable('');
    self.otpChallengeId = ko.observable('');
    self.receiptReference = ko.observable('');
    self.selectedAccountLabel = ko.pureComputed(function () {
      var account = self.accounts().find(function (item) { return item.accountId === self.sourceAccountId(); });
      return account ? account.label : '';
    });
    self.selectedBeneficiaryLabel = ko.pureComputed(function () {
      var beneficiary = self.beneficiaries().find(function (item) { return item.beneficiaryId === self.beneficiaryId(); });
      return beneficiary ? beneficiary.label : '';
    });
    self.formattedAmount = ko.pureComputed(function () {
      var value = Number(self.amount());
      return Number.isFinite(value) ? new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(value) : '—';
    });
    self.reviewTransfer = function () {
      var value = Number(self.amount());
      if (!Number.isFinite(value) || value <= 0) {
        self.message('Enter a transfer amount greater than zero.');
        return false;
      }
      self.step('review');
      self.message('Review the transfer details before requesting an OTP.');
      return false;
    };
    self.backToForm = function () { self.step('form'); };
    self.requestOtp = function () {
      self.otpChallengeId('local-challenge-' + Date.now());
      self.otpCode('');
      self.step('otp');
      self.message('OTP challenge prepared. Connect this action to the shared OtpService.');
    };
    self.confirmTransfer = function () {
      if (!/^[0-9]{6}$/.test(self.otpCode().trim())) {
        self.message('Enter the six-digit OTP.');
        return false;
      }
      self.receiptReference('TXN-' + Date.now().toString(36).toUpperCase());
      self.step('receipt');
      self.message('Transfer receipt created locally. Server confirmation will replace this after API integration.');
      return false;
    };
    self.startAnotherTransfer = function () {
      self.amount('');
      self.narration('');
      self.otpCode('');
      self.step('form');
      self.message('Enter transfer details to continue.');
    };
    self.connected = function () {
      accUtils.announce('Transfer money page loaded.', 'assertive');
      document.title = 'Transfer Money';
    };
  }
  return TransferViewModel;
});
