define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/api-error',
  'oj-c/input-text',
  'oj-c/input-password',
  'oj-c/input-date-mask',
  'oj-c/button',
  'oj-c/form-layout'
], function (ko, accUtils, registry, apiError) {
  function fieldMessages(text) {
    return text ? [{ severity: 'error', summary: text, detail: text }] : [];
  }

  function dateValue(value) {
    if (!value) {
      return '';
    }
    if (typeof value === 'string') {
      return value.substring(0, 10);
    }
    if (value instanceof Date && !Number.isNaN(value.getTime())) {
      var month = String(value.getMonth() + 1).padStart(2, '0');
      var day = String(value.getDate()).padStart(2, '0');
      return value.getFullYear() + '-' + month + '-' + day;
    }
    return String(value).substring(0, 10);
  }

  function RegisterViewModel() {
    var self = this;
    self.firstName = ko.observable('');
    self.lastName = ko.observable('');
    self.username = ko.observable('');
    self.email = ko.observable('');
    self.mobileNumber = ko.observable('');
    self.dateOfBirth = ko.observable(null);
    self.password = ko.observable('');
    self.confirmPassword = ko.observable('');
    self.firstNameMessages = ko.observableArray([]);
    self.lastNameMessages = ko.observableArray([]);
    self.usernameMessages = ko.observableArray([]);
    self.emailMessages = ko.observableArray([]);
    self.mobileMessages = ko.observableArray([]);
    self.dobMessages = ko.observableArray([]);
    self.passwordMessages = ko.observableArray([]);
    self.confirmMessages = ko.observableArray([]);
    self.formError = ko.observable('');
    self.reference = ko.observable('');
    self.busy = ko.observable(false);
    self.registered = ko.observable(false);
    self.customerNumber = ko.observable('');

    self.register = function () {
      var dob = dateValue(self.dateOfBirth());
      var errors = {
        firstName: registry.validation.personName(self.firstName(), 'a first name'),
        lastName: registry.validation.personName(self.lastName(), 'a last name'),
        username: registry.validation.username(self.username()),
        email: registry.validation.email(self.email()),
        mobileNumber: registry.validation.mobile(self.mobileNumber()),
        dateOfBirth: registry.validation.dateOfBirth(dob),
        password: registry.validation.password(self.password()),
        confirmPassword: registry.validation.passwordMatch(self.password(), self.confirmPassword())
      };
      self.firstNameMessages(fieldMessages(errors.firstName));
      self.lastNameMessages(fieldMessages(errors.lastName));
      self.usernameMessages(fieldMessages(errors.username));
      self.emailMessages(fieldMessages(errors.email));
      self.mobileMessages(fieldMessages(errors.mobileNumber));
      self.dobMessages(fieldMessages(errors.dateOfBirth));
      self.passwordMessages(fieldMessages(errors.password));
      self.confirmMessages(fieldMessages(errors.confirmPassword));
      self.formError('');
      self.reference('');
      var firstError = Object.keys(errors).map(function (key) { return errors[key]; }).find(Boolean);
      if (firstError) {
        self.formError(firstError);
        return false;
      }
      if (self.busy()) {
        return false;
      }
      self.busy(true);
      registry.auth.register({
        username: self.username().trim(),
        email: self.email().trim(),
        password: self.password(),
        firstName: self.firstName().trim(),
        lastName: self.lastName().trim(),
        dateOfBirth: dob,
        mobileNumber: self.mobileNumber().trim()
      }).then(function (result) {
        self.password('');
        self.confirmPassword('');
        self.customerNumber(result && result.customerNumber ? result.customerNumber : '');
        self.registered(true);
        accUtils.announce('Registration completed.', 'polite');
      }).catch(function (error) {
        self.password('');
        self.confirmPassword('');
        self.firstNameMessages(fieldMessages(apiError.fieldMessage(error, 'firstName')));
        self.lastNameMessages(fieldMessages(apiError.fieldMessage(error, 'lastName')));
        self.usernameMessages(fieldMessages(apiError.fieldMessage(error, 'username')));
        self.emailMessages(fieldMessages(apiError.fieldMessage(error, 'email')));
        self.mobileMessages(fieldMessages(apiError.fieldMessage(error, 'mobileNumber')));
        self.dobMessages(fieldMessages(apiError.fieldMessage(error, 'dateOfBirth')));
        self.passwordMessages(fieldMessages(apiError.fieldMessage(error, 'password')));
        self.formError((error && error.message) || 'Registration could not be completed.');
        if (error && error.correlationId) {
          self.reference('Reference ' + error.correlationId + '.');
        }
      }).finally(function () {
        self.busy(false);
      });
      return false;
    };

    self.goToSignIn = function () {
      registry.go('login');
    };

    self.connected = function () {
      accUtils.announce('Register for Internet Banking.', 'polite');
      document.title = 'Register | Internet Banking';
    };

    self.disconnected = function () {
      self.password('');
      self.confirmPassword('');
    };
  }

  return RegisterViewModel;
});
