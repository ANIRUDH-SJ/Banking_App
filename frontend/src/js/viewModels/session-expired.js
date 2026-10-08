define(['../accUtils', '../services/registry', 'oj-c/button'], function (accUtils, registry) {
  function SessionExpiredViewModel() {
    this.signIn = function () {
      registry.go('login');
    };
    this.connected = function () {
      accUtils.announce('Your ORACLE INTERNATIONAL BANK (OIB) session has ended.', 'assertive');
      document.title = 'Session ended | ORACLE INTERNATIONAL BANK (OIB)';
    };
  }
  return SessionExpiredViewModel;
});
