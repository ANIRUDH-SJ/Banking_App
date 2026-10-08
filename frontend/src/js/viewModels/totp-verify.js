define([
  'knockout',
  '../accUtils',
  '../services/registry',
  'oj-c/input-text',
  'oj-c/button',
  'oj-c/form-layout'
], function (ko, accUtils, registry) {
  function fieldMessages(text) {
    return text ? [{ severity: 'error', summary: text, detail: text }] : [];
  }

  function TotpVerifyViewModel() {
    var self = this;
    self.code = ko.observable('');
    self.codeMessages = ko.observableArray([]);
    self.formError = ko.observable('');
    self.reference = ko.observable('');
    self.busy = ko.observable(false);

    self.verify = function () {
      var codeError = registry.validation.otp(self.code());
      self.codeMessages(fieldMessages(codeError));
      self.reference('');
      var challengeId = registry.authFlow.getChallengeId();
      if (!challengeId) {
        registry.go('login');
        return false;
      }
      if (codeError) {
        self.formError(codeError);
        return false;
      }
      if (self.busy()) {
        return false;
      }
      self.formError('');
      self.busy(true);
      registry.auth.verifyTotp(challengeId, self.code().trim()).then(function (authentication) {
        var saved = registry.session.save(authentication);
        self.code('');
        registry.authFlow.clear();
        if (!saved) {
          self.formError('Sign-in completed, but the session could not be stored.');
          return;
        }
        registry.go(registry.routeGuard.home(saved));
      }).catch(function (error) {
        self.code('');
        self.formError((error && error.message) || 'The authenticator code was not accepted.');
        if (error && error.correlationId) {
          self.reference('Reference ' + error.correlationId + '.');
        }
      }).finally(function () {
        self.busy(false);
      });
      return false;
    };

    self.restart = function () {
      registry.authFlow.clear();
      registry.go('login');
      return false;
    };

    self.connected = function () {
      accUtils.announce('Enter the Microsoft Authenticator code.', 'polite');
      document.title = 'Authenticator code | ORACLE INTERNATIONAL BANK (OIB)';
      if (!registry.authFlow.getChallengeId()) {
        registry.go('login');
      }
    };

    self.disconnected = function () {
      self.code('');
    };
  }

  return TotpVerifyViewModel;
});
