define(['knockout', '../accUtils', 'ojs/ojbutton', 'ojs/ojpanel'], function (ko, accUtils) {
  function BeneficiariesViewModel() {
    var self = this;
    self.message = ko.observable('Manage beneficiaries here. Verification will use the shared OTP flow.');
    self.form = {
      nickname: ko.observable(''),
      beneficiaryName: ko.observable(''),
      accountNumber: ko.observable(''),
      ifscCode: ko.observable(''),
      bankName: ko.observable('')
    };
    self.beneficiaries = ko.observableArray([
      { beneficiaryId: 1, nickname: 'Home', beneficiaryName: 'Anita Kumar', maskedAccountNumber: '•••• 8821', ifscCode: 'HDFC0001234', bankName: 'HDFC Bank', status: 'ACTIVE' },
      { beneficiaryId: 2, nickname: 'Savings', beneficiaryName: 'Rahul Kumar', maskedAccountNumber: '•••• 4118', ifscCode: 'SBIN0000456', bankName: 'State Bank of India', status: 'PENDING' }
    ]);
    self.addBeneficiary = function () {
      var nickname = self.form.nickname().trim();
      var name = self.form.beneficiaryName().trim();
      var accountNumber = self.form.accountNumber().trim();
      var ifscCode = self.form.ifscCode().trim().toUpperCase();
      var bankName = self.form.bankName().trim();
      if (!nickname || !name || !bankName || !/^[0-9]{10,20}$/.test(accountNumber) || !/^[A-Za-z]{4}0[A-Za-z0-9]{6}$/.test(ifscCode)) {
        self.message('Enter a nickname, name, bank, a 10–20 digit account number, and a valid IFSC code.');
        return false;
      }
      self.beneficiaries.push({ beneficiaryId: Date.now(), nickname: nickname, beneficiaryName: name, maskedAccountNumber: '•••• ' + accountNumber.slice(-4), ifscCode: ifscCode, bankName: bankName, status: 'PENDING' });
      Object.keys(self.form).forEach(function (key) { self.form[key](''); });
      self.message('Beneficiary added. Use Verify when the shared OTP flow is connected.');
      return false;
    };
    self.requestActivation = function (beneficiary) {
      self.message('Verification challenge requested for ' + beneficiary.nickname + '. Connect this action to OtpService when available.');
    };
    self.disableBeneficiary = function (beneficiary) {
      self.beneficiaries.remove(beneficiary);
      self.message(beneficiary.nickname + ' was removed from this local screen.');
    };
    self.connected = function () {
      accUtils.announce('Beneficiaries page loaded.', 'assertive');
      document.title = 'Beneficiaries';
    };
  }
  return BeneficiariesViewModel;
});
