define([
  'knockout',
  '../accUtils',
  '../app-context',
  'ojs/ojbutton',
  'ojs/ojinputtext',
  'ojs/ojlabel'
], function (ko, accUtils, app) {
  'use strict';

  function RegisterViewModel() {
    var self = this;
    self.username = ko.observable('');
    self.email = ko.observable('');
    self.password = ko.observable('');
    self.firstName = ko.observable('');
    self.lastName = ko.observable('');
    self.dateOfBirth = ko.observable('');
    self.mobileNumber = ko.observable('');
    self.errors = {
      username: ko.observable(''),
      email: ko.observable(''),
      password: ko.observable(''),
      firstName: ko.observable(''),
      lastName: ko.observable(''),
      dateOfBirth: ko.observable(''),
      mobileNumber: ko.observable('')
    };
    self.formError = ko.observable('');
    self.loading = ko.observable(false);
    self.completed = ko.observable(false);
    self.customerNumber = ko.observable('');

    function apply(fieldErrors) {
      Object.keys(self.errors).forEach(function (key) {
        self.errors[key]((fieldErrors && fieldErrors[key]) || '');
      });
    }

    self.register = function () {
      if (self.loading()) {
        return;
      }
      self.formError('');
      var values = {
        username: self.username(),
        email: self.email(),
        password: self.password(),
        firstName: self.firstName(),
        lastName: self.lastName(),
        dateOfBirth: self.dateOfBirth(),
        mobileNumber: self.mobileNumber()
      };
      var validation = app.validation.registration(values);
      apply(validation.fieldErrors);
      if (!validation.valid) {
        return;
      }
      self.loading(true);
      app.auth.register(values).then(function (response) {
        self.password('');
        self.customerNumber(response && response.customerNumber ? response.customerNumber : '');
        self.completed(true);
        accUtils.announce('Registration completed.', 'assertive');
      }).catch(function (error) {
        self.password('');
        apply({
          username: app.errorMessage.field(error, 'username'),
          email: app.errorMessage.field(error, 'email'),
          password: app.errorMessage.field(error, 'password'),
          firstName: app.errorMessage.field(error, 'firstName'),
          lastName: app.errorMessage.field(error, 'lastName'),
          dateOfBirth: app.errorMessage.field(error, 'dateOfBirth'),
          mobileNumber: app.errorMessage.field(error, 'mobileNumber')
        });
        self.formError(app.errorMessage.message(error, 'Registration could not be completed.'));
      }).finally(function () {
        self.loading(false);
      });
    };

    self.goToLogin = function () {
      app.go('login');
    };

    this.connected = function () {
      if (!app.ensure('register')) {
        return;
      }
      accUtils.announce('Registration page loaded.', 'assertive');
      document.title = 'Register';
    };

    this.disconnected = function () {
      self.password('');
    };
  }

  return RegisterViewModel;
});
