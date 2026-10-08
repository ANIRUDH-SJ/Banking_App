define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/api-error',
  '../services/format',
  'oj-c/input-text',
  'oj-c/button',
  'oj-c/form-layout',
  'oj-c/skeleton'
], function (ko, accUtils, registry, apiError, format) {
  function fieldMessages(text) {
    return text ? [{ severity: 'error', summary: text, detail: text }] : [];
  }

  function ProfileViewModel() {
    var self = this;
    var generation = 0;
    self.loading = ko.observable(true);
    self.busy = ko.observable(false);
    self.formError = ko.observable('');
    self.formSuccess = ko.observable('');
    self.reference = ko.observable('');
    self.customerNumber = ko.observable('');
    self.dateOfBirth = ko.observable('');
    self.kycStatus = ko.observable('');
    self.profileStatus = ko.observable('');
    self.firstName = ko.observable('');
    self.lastName = ko.observable('');
    self.mobileNumber = ko.observable('');
    self.accounts = ko.observableArray([]);
    self.accountsLoading = ko.observable(true);
    self.accountsError = ko.observable('');
    self.firstNameMessages = ko.observableArray([]);
    self.lastNameMessages = ko.observableArray([]);
    self.mobileMessages = ko.observableArray([]);

    self.activeAccounts = ko.pureComputed(function () {
      return self.accounts().filter(function (account) { return account.accountStatus === 'ACTIVE'; });
    });
    self.rupeeAvailable = ko.pureComputed(function () {
      return self.activeAccounts().filter(function (account) {
        return (account.currencyCode || 'INR') === 'INR';
      }).reduce(function (total, account) { return total + Number(account.availableBalance || 0); }, 0);
    });
    self.walletCount = ko.pureComputed(function () {
      return self.activeAccounts().filter(function (account) {
        return (account.currencyCode || 'INR') !== 'INR';
      }).length;
    });
    self.money = format.formatMoney;
    self.maskAccount = format.maskAccount;
    self.accountName = function (account) {
      return account.nickname || format.labelize(account.accountType) + ' account';
    };
    self.openAccounts = function () {
      registry.go('accounts');
      return false;
    };

    self.save = function () {
      var errors = {
        firstName: registry.validation.personName(self.firstName(), 'a first name'),
        lastName: registry.validation.personName(self.lastName(), 'a last name'),
        mobileNumber: registry.validation.mobile(self.mobileNumber())
      };
      self.firstNameMessages(fieldMessages(errors.firstName));
      self.lastNameMessages(fieldMessages(errors.lastName));
      self.mobileMessages(fieldMessages(errors.mobileNumber));
      self.formSuccess('');
      self.reference('');
      if (errors.firstName || errors.lastName || errors.mobileNumber) {
        self.formError(errors.firstName || errors.lastName || errors.mobileNumber);
        return false;
      }
      if (self.busy()) {
        return false;
      }
      self.formError('');
      self.busy(true);
      registry.profile.update({
        firstName: self.firstName().trim(),
        lastName: self.lastName().trim(),
        mobileNumber: self.mobileNumber().trim()
      }).then(function (profile) {
        applyProfile(profile);
        self.formSuccess('Profile updated.');
        registry.events.emit('profile-changed', profile);
        accUtils.announce('Profile updated.', 'polite');
      }).catch(function (error) {
        self.firstNameMessages(fieldMessages(apiError.fieldMessage(error, 'firstName')));
        self.lastNameMessages(fieldMessages(apiError.fieldMessage(error, 'lastName')));
        self.mobileMessages(fieldMessages(apiError.fieldMessage(error, 'mobileNumber')));
        self.formError((error && error.message) || 'The profile could not be updated.');
        if (error && error.correlationId) {
          self.reference('Reference ' + error.correlationId + '.');
        }
      }).finally(function () {
        self.busy(false);
      });
      return false;
    };

    function applyProfile(profile) {
      if (!profile) {
        return;
      }
      self.customerNumber(profile.customerNumber || '');
      self.dateOfBirth(format.formatDate(profile.dateOfBirth));
      self.kycStatus(format.labelize(profile.kycStatus));
      self.profileStatus(profile.active ? 'Active' : 'Inactive');
      self.firstName(profile.firstName || '');
      self.lastName(profile.lastName || '');
      self.mobileNumber(profile.mobileNumber || '');
    }

    self.connected = function () {
      var ticket = ++generation;
      accUtils.announce('Customer profile.', 'polite');
      document.title = 'Profile | Internet Banking';
      self.loading(true);
      self.formError('');
      self.formSuccess('');
      self.accounts([]);
      self.accountsLoading(true);
      self.accountsError('');
      registry.profile.get().then(function (profile) {
        if (ticket === generation) {
          applyProfile(profile);
        }
      }).catch(function (error) {
        if (ticket !== generation) {
          return;
        }
        self.formError((error && error.message) || 'The profile could not be loaded.');
        if (error && error.correlationId) {
          self.reference('Reference ' + error.correlationId + '.');
        }
      }).finally(function () {
        if (ticket === generation) {
          self.loading(false);
        }
      });
      registry.accounts.getAccounts().then(function (accounts) {
        if (ticket === generation) {
          self.accounts(format.asList(accounts));
        }
      }).catch(function (error) {
        if (ticket === generation) {
          self.accountsError((error && error.message) || 'Your accounts could not be loaded.');
        }
      }).finally(function () {
        if (ticket === generation) {
          self.accountsLoading(false);
        }
      });
    };

    self.disconnected = function () {
      generation += 1;
    };
  }

  return ProfileViewModel;
});
