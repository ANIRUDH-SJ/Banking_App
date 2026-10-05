define([], function () {
  'use strict';

  function AuthService(apiClient, flowState) {
    this.apiClient = apiClient;
    this.flowState = flowState;
  }

  AuthService.prototype.register = function (request) {
    return this.apiClient.post('/api/v1/auth/register', {
      username: String(request.username).trim(),
      email: String(request.email).trim(),
      password: request.password,
      firstName: String(request.firstName).trim(),
      lastName: String(request.lastName).trim(),
      dateOfBirth: String(request.dateOfBirth).trim(),
      mobileNumber: request.mobileNumber
    });
  };

  AuthService.prototype.login = function (usernameOrEmail, password) {
    var self = this;
    var identifier = String(usernameOrEmail).trim();
    return this.apiClient.post('/api/v1/auth/login', {
      usernameOrEmail: identifier,
      password: password
    }).then(function (response) {
      if (response && response.status === 'TOTP_SETUP_REQUIRED') {
        self.flowState.rememberSetup({
          usernameOrEmail: identifier,
          password: password
        });
      } else if (response && response.status === 'TOTP_REQUIRED') {
        self.flowState.rememberChallenge(response.challengeId);
      }
      return response;
    });
  };

  AuthService.prototype.beginTotpSetup = function () {
    var pending = this.flowState.current();
    return this.apiClient.post('/api/v1/auth/totp/setup', pending.credentials);
  };

  AuthService.prototype.confirmTotpSetup = function (code) {
    var self = this;
    var pending = this.flowState.current();
    return this.apiClient.post('/api/v1/auth/totp/confirm', {
      credentials: pending.credentials,
      code: String(code).trim()
    }).then(function (response) {
      self.flowState.clear();
      return response;
    });
  };

  AuthService.prototype.verifyLoginTotp = function (code) {
    var self = this;
    var pending = this.flowState.current();
    return this.apiClient.post('/api/v1/auth/login/verify-totp', {
      challengeId: pending.challengeId,
      code: String(code).trim()
    }).then(function (response) {
      self.flowState.clear();
      return response;
    });
  };

  AuthService.prototype.requestPasswordReset = function (usernameOrEmail) {
    return this.apiClient.post('/api/v1/auth/password-reset/challenges', {
      usernameOrEmail: String(usernameOrEmail).trim()
    });
  };

  AuthService.prototype.confirmPasswordReset = function (request) {
    return this.apiClient.post('/api/v1/auth/password-reset/confirm', {
      challengeId: request.challengeId,
      code: String(request.code).trim(),
      newPassword: request.newPassword
    });
  };

  return AuthService;
});
