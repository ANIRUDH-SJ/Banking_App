(function (factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  }
})(function () {
  var usernameOrEmail = '';
  var password = '';
  var challengeId = '';

  return {
    setCredentials: function (user, secret) {
      usernameOrEmail = user || '';
      password = secret || '';
    },
    getCredentials: function () {
      return { usernameOrEmail: usernameOrEmail, password: password };
    },
    hasCredentials: function () {
      return !!(usernameOrEmail && password);
    },
    clearPassword: function () {
      password = '';
    },
    setChallengeId: function (value) {
      challengeId = value || '';
    },
    getChallengeId: function () {
      return challengeId;
    },
    clear: function () {
      usernameOrEmail = '';
      password = '';
      challengeId = '';
    }
  };
});
