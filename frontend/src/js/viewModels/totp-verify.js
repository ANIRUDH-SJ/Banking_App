define([
  'knockout',
  '../accUtils',
  '../app-context',
  'ojs/ojbutton',
  'ojs/ojinputtext',
  'ojs/ojlabel'
], function (ko, accUtils, app) {
  'use strict';

  function TotpVerifyViewModel() {
    var self = this;
    self.code = ko.observable('');
    self.codeError = ko.observable('');
    self.formError = ko.observable('');
    self.loading = ko.observable(false);

    self.verify = function () {
      if (self.loading()) {
        return;
      }
      var codeError = app.validation.otpCode(self.code());
      self.codeError(codeError);
      if (codeError) {
        return;
      }
      self.loading(true);
      self.formError('');
      app.auth.verifyLoginTotp(self.code()).then(function (authentication) {
        self.code('');
        app.session.establish(authentication);
        app.go('dashboard');
      }).catch(function (error) {
        self.code('');
        self.codeError(app.errorMessage.field(error, 'code'));
        self.formError(app.errorMessage.message(error, 'The authenticator code was not accepted.'));
      }).finally(function () {
        self.loading(false);
      });
    };

    self.cancel = function () {
      self.code('');
      app.flowState.clear();
      app.go('login');
    };

    this.connected = function () {
      if (!app.ensure('totp-verify')) {
        return;
      }
      accUtils.announce('Authenticator verification page loaded.', 'assertive');
      document.title = 'Verify sign-in';
    };

    this.disconnected = function () {
      self.code('');
    };
  }

  return TotpVerifyViewModel;
});
