define([
  'knockout',
  '../accUtils',
  '../services/registry',
  'oj-c/input-text',
  'oj-c/button',
  'oj-c/form-layout',
  'oj-c/progress-circle'
], function (ko, accUtils, registry) {
  function fieldMessages(text) {
    return text ? [{ severity: 'error', summary: text, detail: text }] : [];
  }

  function TotpSetupViewModel() {
    var self = this;
    self.qrCodeDataUri = ko.observable('');
    self.manualEntryKey = ko.observable('');
    self.issuer = ko.observable('Internet Banking');
    self.accountName = ko.observable('');
    self.code = ko.observable('');
    self.codeMessages = ko.observableArray([]);
    self.formError = ko.observable('');
    self.reference = ko.observable('');
    self.loading = ko.observable(true);
    self.busy = ko.observable(false);
    self.showKey = ko.observable(false);
    var generation = 0;

    function clearSecrets() {
      self.qrCodeDataUri('');
      self.manualEntryKey('');
      self.code('');
    }

    self.toggleKey = function () {
      self.showKey(!self.showKey());
    };

    self.confirm = function () {
      var codeError = registry.validation.otp(self.code());
      self.codeMessages(fieldMessages(codeError));
      self.reference('');
      if (codeError) {
        self.formError(codeError);
        return false;
      }
      if (self.busy()) {
        return false;
      }
      self.formError('');
      var credentials = registry.authFlow.getCredentials();
      if (!credentials.password) {
        registry.go('login');
        return false;
      }
      self.busy(true);
      registry.auth.confirmTotp(credentials.usernameOrEmail, credentials.password, self.code().trim())
        .then(function () {
          return registry.auth.login(credentials.usernameOrEmail, credentials.password);
        })
        .then(function (result) {
          credentials.password = '';
          registry.authFlow.clear();
          clearSecrets();
          if (result && result.status === 'TOTP_REQUIRED' && result.challengeId) {
            registry.authFlow.setChallengeId(result.challengeId);
            registry.go('totp-verify');
            return;
          }
          registry.go('login');
        })
        .catch(function (error) {
          credentials.password = '';
          self.code('');
          self.formError((error && error.message) || 'The authenticator code was not accepted.');
          if (error && error.correlationId) {
            self.reference('Reference ' + error.correlationId + '.');
          }
        })
        .finally(function () {
          self.busy(false);
        });
      return false;
    };

    self.connected = function () {
      var ticket = ++generation;
      accUtils.announce('Set up Microsoft Authenticator.', 'polite');
      document.title = 'Set up Microsoft Authenticator | Internet Banking';
      if (!registry.authFlow.hasCredentials()) {
        registry.go('login');
        return;
      }
      var credentials = registry.authFlow.getCredentials();
      self.loading(true);
      self.formError('');
      registry.auth.setupTotp(credentials.usernameOrEmail, credentials.password).then(function (setup) {
        if (ticket !== generation || !setup) {
          return;
        }
        self.qrCodeDataUri(setup.qrCodeDataUri || '');
        self.manualEntryKey(setup.manualEntryKey || '');
        self.issuer(setup.issuer || 'Internet Banking');
        self.accountName(setup.accountName || credentials.usernameOrEmail);
      }).catch(function (error) {
        if (ticket !== generation) {
          return;
        }
        self.formError((error && error.message) || 'Authenticator setup could not be started.');
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
      clearSecrets();
    };
  }

  return TotpSetupViewModel;
});
