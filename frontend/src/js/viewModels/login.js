define([
  'knockout',
  '../accUtils',
  '../services/registry',
  'ojs/ojinputtext',
  'ojs/ojbutton',
  'ojs/ojformlayout'
], function (ko, accUtils, registry) {
  function fieldMessages(text) {
    return text ? [{ severity: 'error', summary: text, detail: '' }] : [];
  }

  function LoginViewModel() {
    var self = this;
    self.username = ko.observable('');
    self.password = ko.observable('');
    self.usernameMessages = ko.observableArray([]);
    self.passwordMessages = ko.observableArray([]);
    self.formError = ko.observable('');
    self.reference = ko.observable('');
    self.busy = ko.observable(false);

    self.signIn = function () {
      var usernameError = registry.validation.usernameOrEmail(self.username());
      var passwordError = registry.validation.required(self.password(), 'Enter your password.');
      self.usernameMessages(fieldMessages(usernameError));
      self.passwordMessages(fieldMessages(passwordError));
      self.reference('');
      if (usernameError || passwordError) {
        self.formError([usernameError, passwordError].filter(Boolean).join(' '));
        return false;
      }
      if (self.busy()) {
        return false;
      }
      self.formError('');
      self.busy(true);
      registry.auth.login(self.username().trim(), self.password()).then(function (result) {
        var username = self.username().trim();
        var password = self.password();
        self.password('');
        if (result && result.status === 'TOTP_SETUP_REQUIRED') {
          registry.authFlow.setCredentials(username, password);
          registry.go('totp-setup');
          return;
        }
        if (result && result.status === 'TOTP_REQUIRED' && result.challengeId) {
          registry.authFlow.clear();
          registry.authFlow.setChallengeId(result.challengeId);
          registry.go('totp-verify');
          return;
        }
        self.formError('Sign-in did not return a verification challenge.');
      }).catch(function (error) {
        self.password('');
        self.formError((error && error.message) || 'Sign-in failed.');
        if (error && error.retryAfter) {
          self.reference('Try again in ' + error.retryAfter + ' seconds.');
        } else if (error && error.correlationId) {
          self.reference('Reference ' + error.correlationId + '.');
        }
      }).finally(function () {
        self.busy(false);
      });
      return false;
    };

    self.openRecovery = function () {
      registry.go('password-recovery');
      return false;
    };

    self.openRegister = function () {
      registry.go('register');
      return false;
    };

    self.connected = function () {
      accUtils.announce('Sign in to Internet Banking.', 'polite');
      document.title = 'Sign in | Internet Banking';
    };
  }

  return LoginViewModel;
});
