define(['../accUtils', '../services/registry', 'oj-c/button'], function (accUtils, registry) {
  function SessionExpiredViewModel() {
    this.signIn = function () {
      registry.go('login');
    };
    this.connected = function () {
      accUtils.announce('Your Internet Banking session has ended.', 'assertive');
      document.title = 'Session ended | Internet Banking';
    };
  }
  return SessionExpiredViewModel;
});
