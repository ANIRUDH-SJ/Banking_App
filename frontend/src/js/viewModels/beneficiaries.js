define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/format',
  '../services/ui-support',
  '../services/beneficiary-service',
  'oj-c/button',
  'oj-c/input-text',
  'oj-c/badge',
  'oj-c/form-layout',
  'oj-c/skeleton'
], function (ko, accUtils, registry, format, ui, BeneficiaryService) {
  var ACCOUNT = /^[0-9]{10,20}$/;
  var IFSC = /^[A-Za-z]{4}0[A-Za-z0-9]{6}$/;

  function BeneficiariesViewModel() {
    var self = this;
    var generation = 0;
    var payees = new BeneficiaryService(registry.apiClient);

    self.problem = new ui.Problem();
    self.success = ko.observable('');
    self.loading = ko.observable(true);
    self.busy = ko.observable(false);
    self.items = ko.observableArray([]);
    self.adding = ko.observable(false);
    self.activatingId = ko.observable(null);
    self.removingId = ko.observable(null);
    self.challengeId = ko.observable('');
    self.otpCode = ko.observable('');
    self.otpMessages = ko.observableArray([]);

    self.form = {
      nickname: ko.observable(''),
      beneficiaryName: ko.observable(''),
      accountNumber: ko.observable(''),
      confirmAccount: ko.observable(''),
      ifscCode: ko.observable(''),
      bankName: ko.observable('')
    };
    self.formMessages = {};
    Object.keys(self.form).forEach(function (key) {
      self.formMessages[key] = ko.observableArray([]);
    });

    self.activeCount = ko.pureComputed(function () {
      return self.items().filter(function (item) { return item.status === 'ACTIVE'; }).length;
    });

    self.label = format.labelize;
    self.statusVariant = ui.statusVariant;

    self.initials = function (item) {
      return String(item.nickname || item.beneficiaryName || '?').trim().split(/\s+/).slice(0, 2).map(function (word) {
        return word.charAt(0).toUpperCase();
      }).join('');
    };

    self.openAdd = function () {
      Object.keys(self.form).forEach(function (key) {
        self.form[key]('');
        self.formMessages[key]([]);
      });
      self.success('');
      self.problem.clear();
      self.adding(true);
      window.setTimeout(function () {
        var first = document.querySelector('.nb-add-payee input');
        if (first) {
          first.focus();
        }
      }, 50);
    };

    self.cancelAdd = function () {
      self.adding(false);
      self.problem.clear();
    };

    function check() {
      var f = self.form;
      var value = function (key) { return String(f[key]() || '').trim(); };
      var errors = {
        nickname: value('nickname') ? (value('nickname').length > 100 ? 'Keep the nickname under 100 characters.' : '') : 'Enter a nickname.',
        beneficiaryName: value('beneficiaryName') ? '' : 'Enter the account holder\u2019s name.',
        accountNumber: ACCOUNT.test(value('accountNumber')) ? '' : 'Enter the 10 to 20 digit account number.',
        confirmAccount: value('confirmAccount') === value('accountNumber') ? '' : 'The account numbers do not match.',
        ifscCode: IFSC.test(value('ifscCode')) ? '' : 'Enter an 11-character IFSC, for example HDFC0001234.',
        bankName: value('bankName') ? '' : 'Enter the bank name.'
      };
      Object.keys(errors).forEach(function (key) {
        self.formMessages[key](ui.messages(errors[key]));
      });
      return Object.keys(errors).filter(function (key) { return errors[key]; }).length === 0;
    }

    self.save = function () {
      if (!check()) {
        self.problem.show('Check the highlighted fields.');
        return false;
      }
      if (self.busy()) {
        return false;
      }
      var f = self.form;
      self.busy(true);
      self.problem.clear();
      payees.create({
        nickname: f.nickname().trim(),
        beneficiaryName: f.beneficiaryName().trim(),
        accountNumber: f.accountNumber().trim(),
        ifscCode: f.ifscCode().trim().toUpperCase(),
        bankName: f.bankName().trim()
      }).then(function (created) {
        self.adding(false);
        self.items.push(created);
        self.success(created.nickname + ' was added. Activate it with a one-time code before sending money.');
        self.startActivation(created);
      }).catch(function (error) {
        ['nickname', 'beneficiaryName', 'accountNumber', 'ifscCode', 'bankName'].forEach(function (key) {
          self.formMessages[key](ui.messages(ui.fieldMessage(error, key)));
        });
        self.problem.set(error, 'The beneficiary could not be added.');
      }).finally(function () {
        self.busy(false);
      });
      return false;
    };

    self.startActivation = function (item) {
      self.removingId(null);
      self.problem.clear();
      self.otpCode('');
      self.otpMessages([]);
      self.busy(true);
      registry.otp.requestBeneficiaryActivation(item.beneficiaryId).then(function (challenge) {
        self.challengeId(challenge && challenge.challengeId);
        self.activatingId(item.beneficiaryId);
        accUtils.announce('A one-time code has been sent to your registered email and mobile.', 'polite');
      }).catch(function (error) {
        self.problem.set(error, 'The activation code could not be sent.');
      }).finally(function () {
        self.busy(false);
      });
    };

    self.activate = function (item) {
      var error = registry.otp.validateCode(self.otpCode());
      self.otpMessages(ui.messages(error));
      if (error || self.busy()) {
        return false;
      }
      self.busy(true);
      payees.activate(item.beneficiaryId, self.challengeId(), String(self.otpCode()).trim()).then(function (updated) {
        self.items.replace(item, updated || Object.assign({}, item, { status: 'ACTIVE' }));
        self.activatingId(null);
        self.success(item.nickname + ' is active. You can now send money to them.');
        accUtils.announce(item.nickname + ' activated.', 'polite');
      }).catch(function (failure) {
        self.otpMessages(ui.messages(ui.fieldMessage(failure, 'otpCode') || (failure && failure.message) || 'The code was not accepted.'));
      }).finally(function () {
        self.busy(false);
      });
      return false;
    };

    self.cancelActivation = function () {
      self.activatingId(null);
    };

    self.askRemove = function (item) {
      self.activatingId(null);
      self.removingId(item.beneficiaryId);
    };

    self.keep = function () {
      self.removingId(null);
    };

    self.remove = function (item) {
      if (self.busy()) {
        return;
      }
      self.busy(true);
      payees.disable(item.beneficiaryId).then(function () {
        self.items.remove(item);
        self.removingId(null);
        self.success(item.nickname + ' was removed.');
      }).catch(function (error) {
        self.problem.set(error, 'The beneficiary could not be removed.');
      }).finally(function () {
        self.busy(false);
      });
    };

    self.pay = function (item) {
      ui.hand('transfer.beneficiaryId', item.beneficiaryId);
      registry.go('transfer');
    };

    self.connected = function () {
      var ticket = ++generation;
      accUtils.announce('Beneficiaries.', 'polite');
      document.title = 'Beneficiaries | Internet Banking';
      self.loading(true);
      self.problem.clear();
      self.success('');
      payees.list().then(function (list) {
        if (ticket === generation) {
          self.items(format.asList(list));
        }
      }).catch(function (error) {
        if (ticket === generation) {
          self.problem.set(error, 'Beneficiaries could not be loaded.');
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

  return BeneficiariesViewModel;
});
