define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/beneficiary-service',
  '../services/member2-style',
  'oj-c/button'
], function (ko, accUtils, registry, BeneficiaryService) {
  function BeneficiariesViewModel() {
    var self = this;
    var service = new BeneficiaryService(registry.apiClient);
    self.beneficiaries = ko.observableArray([]);
    self.loading = ko.observable(false);
    self.busy = ko.observable(false);
    self.error = ko.observable('');
    self.message = ko.observable('');
    self.activationTarget = ko.observable(null);
    self.activationChallengeId = ko.observable('');
    self.activationCode = ko.observable('');
    self.form = {
      nickname: ko.observable(''),
      beneficiaryName: ko.observable(''),
      accountNumber: ko.observable(''),
      ifscCode: ko.observable(''),
      bankName: ko.observable('')
    };

    self.refresh = function () {
      self.loading(true);
      self.error('');
      return service.list().then(function (rows) {
        self.beneficiaries(Array.isArray(rows) ? rows : []);
      }).catch(function (error) {
        self.error(error.message || 'Unable to load beneficiaries.');
      }).finally(function () { self.loading(false); });
    };

    self.addBeneficiary = function () {
      if (self.busy()) return false;
      var request = {
        nickname: self.form.nickname().trim(),
        beneficiaryName: self.form.beneficiaryName().trim(),
        accountNumber: self.form.accountNumber().trim(),
        ifscCode: self.form.ifscCode().trim().toUpperCase(),
        bankName: self.form.bankName().trim()
      };
      if (!request.nickname || !request.beneficiaryName || !request.bankName ||
          !/^[0-9]{10,20}$/.test(request.accountNumber) ||
          !/^[A-Z]{4}0[A-Z0-9]{6}$/.test(request.ifscCode)) {
        self.error('Enter a nickname, name, bank, a 10–20 digit account number, and a valid IFSC code.');
        return false;
      }
      self.busy(true);
      self.error('');
      self.message('');
      service.create(request).then(function () {
        Object.keys(self.form).forEach(function (key) { self.form[key](''); });
        self.message('Beneficiary added. Verify it before transferring money.');
        return self.refresh();
      }).catch(function (error) {
        self.error(error.message || 'Unable to add beneficiary.');
      }).finally(function () { self.busy(false); });
      return false;
    };

    self.requestActivation = function (beneficiary) {
      if (self.busy()) return;
      self.busy(true);
      self.error('');
      self.message('');
      registry.otp.requestBeneficiaryActivation(beneficiary.beneficiaryId).then(function (challenge) {
        self.activationTarget(beneficiary);
        self.activationChallengeId(challenge.challengeId);
        self.activationCode('');
        self.message('Enter the one-time code sent for ' + beneficiary.nickname + '.');
      }).catch(function (error) {
        self.error(error.message || 'Unable to request the verification code.');
      }).finally(function () { self.busy(false); });
    };

    self.activate = function () {
      if (self.busy() || !self.activationTarget()) return false;
      var validation = registry.otp.validateCode(self.activationCode());
      if (validation) {
        self.error(validation);
        return false;
      }
      self.busy(true);
      self.error('');
      service.activate(
        self.activationTarget().beneficiaryId,
        self.activationChallengeId(),
        self.activationCode().trim()
      ).then(function () {
        self.message('Beneficiary verified and ready for transfers.');
        self.activationTarget(null);
        self.activationChallengeId('');
        self.activationCode('');
        return self.refresh();
      }).catch(function (error) {
        self.error(error.message || 'Unable to verify beneficiary.');
      }).finally(function () { self.busy(false); });
      return false;
    };

    self.cancelActivation = function () {
      self.activationTarget(null);
      self.activationChallengeId('');
      self.activationCode('');
      self.error('');
    };

    self.disableBeneficiary = function (beneficiary) {
      if (self.busy() || !window.confirm('Disable ' + beneficiary.nickname + '? Transfers to this beneficiary will stop.')) return;
      self.busy(true);
      self.error('');
      service.disable(beneficiary.beneficiaryId).then(function () {
        self.message(beneficiary.nickname + ' disabled.');
        return self.refresh();
      }).catch(function (error) {
        self.error(error.message || 'Unable to disable beneficiary.');
      }).finally(function () { self.busy(false); });
    };

    self.connected = function () {
      accUtils.announce('Beneficiaries page loaded.', 'polite');
      document.title = 'Beneficiaries | Internet Banking';
      self.refresh();
    };
  }
  return BeneficiariesViewModel;
});
