define([
  'knockout',
  '../accUtils',
  '../app-context',
  'ojs/ojbutton',
  'ojs/ojinputtext',
  'ojs/ojlabel'
], function (ko, accUtils, app) {
  'use strict';

  function TotpSetupViewModel() {
    var self = this;
    self.qrCodeDataUri = ko.observable('');
    self.manualEntryKey = ko.observable('');
    self.issuer = ko.observable('');
    self.accountName = ko.observable('');
    self.code = ko.observable('');
    self.codeError = ko.observable('');
    self.formError = ko.observable('');
    self.loading = ko.observable(false);
    self.ready = ko.observable(false);
    self.completed = ko.observable(false);

    function loadSetup() {
      self.loading(true);
      self.formError('');
      app.auth.beginTotpSetup().then(function (setup) {
        self.qrCodeDataUri(setup && setup.qrCodeDataUri ? setup.qrCodeDataUri : '');
        self.manualEntryKey(setup && setup.manualEntryKey ? setup.manualEntryKey : '');
        self.issuer(setup && setup.issuer ? setup.issuer : '');
        self.accountName(setup && setup.accountName ? setup.accountName : '');
        self.ready(true);
      }).catch(function (error) {
        self.formError(app.errorMessage.message(error, 'Authenticator setup could not be started.'));
      }).finally(function () {
        self.loading(false);
      });
    }

    self.confirm = function () {
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
      app.auth.confirmTotpSetup(self.code()).then(function () {
        self.code('');
        self.qrCodeDataUri('');
        self.manualEntryKey('');
        self.completed(true);
        accUtils.announce('Authenticator setup completed.', 'assertive');
      }).catch(function (error) {
        self.code('');
        self.codeError(app.errorMessage.field(error, 'code'));
        self.formError(app.errorMessage.message(error, 'The authenticator code was not accepted.'));
      }).finally(function () {
        self.loading(false);
      });
    };

    self.goToLogin = function () {
      app.flowState.clear();
      self.qrCodeDataUri('');
      self.manualEntryKey('');
      app.go('login');
    };

    this.connected = function () {
      if (!app.ensure('totp-setup')) {
        return;
      }
      accUtils.announce('Authenticator setup page loaded.', 'assertive');
      document.title = 'Set up authenticator';
      loadSetup();
    };

    this.disconnected = function () {
      self.code('');
      self.qrCodeDataUri('');
      self.manualEntryKey('');
      if (!self.completed()) {
        app.flowState.clear();
      }
    };
  }

  return TotpSetupViewModel;
});
