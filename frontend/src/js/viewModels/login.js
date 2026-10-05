define([
  'knockout',
  '../accUtils',
  '../app-context',
  'ojs/ojbutton',
  'ojs/ojinputtext',
  'ojs/ojlabel'
], function (ko, accUtils, app) {
  'use strict';

  function LoginViewModel() {
    var self = this;
    self.usernameOrEmail = ko.observable('');
    self.password = ko.observable('');
    self.usernameError = ko.observable('');
    self.passwordError = ko.observable('');
    self.formError = ko.observable('');
    self.info = ko.observable('');
    self.loading = ko.observable(false);

    self.signIn = function () {
      if (self.loading()) {
        return;
      }
      self.formError('');
      var validation = app.validation.login({
        usernameOrEmail: self.usernameOrEmail(),
        password: self.password()
      });
      self.usernameError(validation.fieldErrors.usernameOrEmail || '');
      self.passwordError(validation.fieldErrors.password || '');
      if (!validation.valid) {
        return;
      }
      self.loading(true);
      app.auth.login(self.usernameOrEmail(), self.password()).then(function (response) {
        self.password('');
        if (response && response.status === 'TOTP_SETUP_REQUIRED') {
          app.go('totp-setup');
          return;
        }
        if (response && response.status === 'TOTP_REQUIRED') {
          app.go('totp-verify');
          return;
        }
        self.formError('Sign-in could not continue. Try again.');
      }).catch(function (error) {
        self.password('');
        self.usernameError(app.errorMessage.field(error, 'usernameOrEmail'));
        self.passwordError(app.errorMessage.field(error, 'password'));
        self.formError(app.errorMessage.message(error, 'Sign-in failed. Check the details and try again.'));
      }).finally(function () {
        self.loading(false);
      });
    };

    self.goToRegister = function () {
      app.go('register');
    };

    self.goToReset = function () {
      app.go('password-reset');
    };

    this.connected = function () {
      if (!app.ensure('login')) {
        return;
      }
      var notice = app.notices.consume();
      if (notice) {
        self.info(notice);
      }
      accUtils.announce('Sign in page loaded.', 'assertive');
      document.title = 'Sign in';
    };

    this.disconnected = function () {
      self.password('');
    };
  }

  return LoginViewModel;
});
