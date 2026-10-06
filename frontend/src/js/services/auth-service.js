(function (factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  }
})(function () {
  var PUBLIC = { auth: false };

  function AuthService(apiClient) {
    this.register = function (request) {
      return apiClient.post('/api/v1/auth/register', request, PUBLIC);
    };

    this.login = function (usernameOrEmail, password) {
      return apiClient.post('/api/v1/auth/login', {
        usernameOrEmail: usernameOrEmail,
        password: password
      }, PUBLIC);
    };

    this.verifyTotp = function (challengeId, code) {
      return apiClient.post('/api/v1/auth/login/verify-totp', {
        challengeId: challengeId,
        code: code
      }, PUBLIC);
    };

    this.requestPasswordReset = function (usernameOrEmail) {
      return apiClient.post('/api/v1/auth/password-reset/challenges', {
        usernameOrEmail: usernameOrEmail
      }, PUBLIC);
    };

    this.confirmPasswordReset = function (request) {
      return apiClient.post('/api/v1/auth/password-reset/confirm', request, PUBLIC);
    };

    this.setupTotp = function (usernameOrEmail, password) {
      return apiClient.post('/api/v1/auth/totp/setup', {
        usernameOrEmail: usernameOrEmail,
        password: password
      }, PUBLIC);
    };

    this.confirmTotp = function (usernameOrEmail, password, code) {
      return apiClient.post('/api/v1/auth/totp/confirm', {
        credentials: {
          usernameOrEmail: usernameOrEmail,
          password: password
        },
        code: code
      }, PUBLIC);
    };
  }

  return AuthService;
});
