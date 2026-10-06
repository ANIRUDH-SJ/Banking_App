define([
  'knockout',
  '../accUtils',
  '../services/registry',
  'oj-c/input-text',
  'oj-c/input-password',
  'oj-c/button',
  'oj-c/form-layout'
], function (ko, accUtils, registry) {
  function fieldMessages(text) {
    return text ? [{ severity: 'error', summary: text, detail: text }] : [];
  }

  function PasswordRecoveryViewModel() {
    var self = this;
    self.step = ko.observable('request');
    self.username = ko.observable('');
    self.code = ko.observable('');
    self.password = ko.observable('');
    self.confirmPassword = ko.observable('');
    self.usernameMessages = ko.observableArray([]);
    self.codeMessages = ko.observableArray([]);
    self.passwordMessages = ko.observableArray([]);
    self.confirmMessages = ko.observableArray([]);
    self.formError = ko.observable('');
    self.reference = ko.observable('');
    self.busy = ko.observable(false);
    var challengeId = '';

    self.requestCode = function () {
      var usernameError = registry.validation.usernameOrEmail(self.username());
      self.usernameMessages(fieldMessages(usernameError));
      self.reference('');
      if (usernameError) {
        self.formError(usernameError);
        return false;
      }
      if (self.busy()) {
        return false;
      }
      self.formError('');
      self.busy(true);
      registry.auth.requestPasswordReset(self.username().trim()).then(function (result) {
        challengeId = result && result.challengeId ? result.challengeId : '';
        self.step('confirm');
        accUtils.announce('If the account can be reset, a code has been sent.', 'polite');
      }).catch(function (error) {
        self.formError((error && error.message) || 'The reset could not be requested.');
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

    self.confirm = function () {
      var codeError = registry.validation.otp(self.code());
      var passwordError = registry.validation.password(self.password());
      var confirmError = registry.validation.passwordMatch(self.password(), self.confirmPassword());
      self.codeMessages(fieldMessages(codeError));
      self.passwordMessages(fieldMessages(passwordError));
      self.confirmMessages(fieldMessages(confirmError));
      self.reference('');
      if (codeError || passwordError || confirmError) {
        self.formError(codeError || passwordError || confirmError);
        return false;
      }
      if (self.busy()) {
        return false;
      }
      self.formError('');
      if (!challengeId) {
        self.formError('Request a code before choosing a new password.');
        return false;
      }
      self.busy(true);
      registry.auth.confirmPasswordReset({
        challengeId: challengeId,
        code: self.code().trim(),
        newPassword: self.password()
      }).then(function () {
        self.password('');
        self.confirmPassword('');
        self.code('');
        challengeId = '';
        self.step('done');
        accUtils.announce('Password reset completed.', 'polite');
      }).catch(function (error) {
        self.password('');
        self.confirmPassword('');
        self.formError((error && error.message) || 'The password was not changed.');
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
      accUtils.announce('Reset your Internet Banking password.', 'polite');
      document.title = 'Reset password | Internet Banking';
    };

    self.disconnected = function () {
      self.password('');
      self.confirmPassword('');
      self.code('');
      challengeId = '';
    };
  }

  return PasswordRecoveryViewModel;
});
