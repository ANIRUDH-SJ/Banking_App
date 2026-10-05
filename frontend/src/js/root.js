define([
  'ojs/ojbootstrap',
  'knockout',
  './appController',
  'ojs/ojknockout',
  'ojs/ojmodule-element',
  'ojs/ojbutton',
  'ojs/ojdialog',
  'ojs/ojdrawerpopup'
], function (Bootstrap, ko, app) {
  Bootstrap.whenDocumentReady().then(function () {
    function init() {
      ko.applyBindings(app, document.getElementById('globalBody'));
    }
    if (document.body.classList.contains('oj-hybrid')) {
      document.addEventListener('deviceready', init);
    } else {
      init();
    }
  });
});
