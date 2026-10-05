define([
  'knockout',
  '../accUtils',
  '../app-context',
  'ojs/ojbutton',
  'ojs/ojinputtext',
  'ojs/ojlabel'
], function (ko, accUtils, app) {
  'use strict';

  function PasswordResetViewModel() {
    var self = this;
    self.step = ko.observable('request');
    self.usernameOrEmail = ko.observable('');
    self.code = ko.observable('');
    self.newPassword = ko.observable('');
    self.confirmPassword = ko.observable('');
    self.challengeId = ko.observable('');
    self.usernameError = ko.observable('');
    self.codeError = ko.observable('');
    self.passwordError = ko.observable('');
    self.confirmError = ko.observable('');
    self.formError = ko.observable('');
    self.info = ko.observable('');
    self.loading = ko.observable(false);
    self.completed = ko.observable(false);

    self.requestCode = function () {
      if (self.loading()) {
        return;
      }
      self.formError('');
      var validation = app.validation.passwordResetRequest({
        usernameOrEmail: self.usernameOrEmail()
      });
      self.usernameError(validation.fieldErrors.usernameOrEmail || '');
      if (!validation.valid) {
        return;
      }
      self.loading(true);
      app.auth.requestPasswordReset(self.usernameOrEmail()).then(function (response) {
        self.challengeId(response && response.challengeId ? response.challengeId : '');
        self.step('confirm');
        self.info('If an account matches those details, a 6-digit code has been sent. The code expires and can be used once.');
        accUtils.announce(self.info(), 'polite');
      }).catch(function (error) {
        self.usernameError(app.errorMessage.field(error, 'usernameOrEmail'));
        self.formError(app.errorMessage.message(error, 'The reset code could not be requested.'));
      }).finally(function () {
        self.loading(false);
      });
    };

    self.confirmReset = function () {
      if (self.loading()) {
        return;
      }
      self.formError('');
      var validation = app.validation.passwordResetConfirm({
        code: self.code(),
        newPassword: self.newPassword(),
        confirmPassword: self.confirmPassword()
      });
      self.codeError(validation.fieldErrors.code || '');
      self.passwordError(validation.fieldErrors.newPassword || '');
      self.confirmError(validation.fieldErrors.confirmPassword || '');
      if (!validation.valid) {
        return;
      }
      self.loading(true);
      app.auth.confirmPasswordReset({
        challengeId: self.challengeId(),
        code: self.code(),
        newPassword: self.newPassword()
      }).then(function () {
        self.code('');
        self.newPassword('');
        self.confirmPassword('');
        self.challengeId('');
        self.completed(true);
        self.step('done');
        accUtils.announce('Password updated. Sign in with the new password.', 'assertive');
      }).catch(function (error) {
        self.newPassword('');
        self.confirmPassword('');
        self.codeError(app.errorMessage.field(error, 'code'));
        self.passwordError(app.errorMessage.field(error, 'newPassword'));
        self.formError(app.errorMessage.message(error, 'The password could not be reset.'));
      }).finally(function () {
        self.loading(false);
      });
    };

    self.goToLogin = function () {
      app.go('login');
    };

    this.connected = function () {
      if (!app.ensure('password-reset')) {
        return;
      }
      accUtils.announce('Password reset page loaded.', 'assertive');
      document.title = 'Reset password';
    };

    this.disconnected = function () {
      self.code('');
      self.newPassword('');
      self.confirmPassword('');
      self.challengeId('');
    };
  }

  return PasswordResetViewModel;
});
