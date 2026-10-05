define([], function () {
  'use strict';

  var pending = null;

  function rememberSetup(credentials) {
    pending = {
      kind: 'setup',
      credentials: {
        usernameOrEmail: credentials.usernameOrEmail,
        password: credentials.password
      }
    };
  }

  function rememberChallenge(challengeId) {
    pending = {
      kind: 'verify',
      challengeId: challengeId
    };
  }

  function current() {
    return pending;
  }

  function clear() {
    pending = null;
  }

  return {
    rememberSetup: rememberSetup,
    rememberChallenge: rememberChallenge,
    current: current,
    clear: clear
  };
});
