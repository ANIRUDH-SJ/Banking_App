define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/api-error',
  '../services/format',
  'ojs/ojinputtext',
  'ojs/ojbutton',
  'ojs/ojformlayout'
], function (ko, accUtils, registry, apiError, format) {
  function fieldMessages(text) {
    return text ? [{ severity: 'error', summary: text, detail: '' }] : [];
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
    self.firstNameMessages = ko.observableArray([]);
    self.lastNameMessages = ko.observableArray([]);
    self.mobileMessages = ko.observableArray([]);

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
    };

    self.disconnected = function () {
      generation += 1;
    };
  }

  return ProfileViewModel;
});
