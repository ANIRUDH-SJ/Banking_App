define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/format',
  '../services/member2-style',
  'oj-c/button'
], function (ko, accUtils, registry, format) {
  function AccountsViewModel() {
    var self = this;
    var requestId = 0;
    self.accounts = ko.observableArray([]);
    self.loading = ko.observable(false);
    self.error = ko.observable('');
    self.lastUpdated = ko.observable('');
    self.money = format.formatMoney;
    self.mask = format.maskAccount;
    self.label = format.labelize;

    self.refreshAccounts = function () {
      var current = ++requestId;
      self.loading(true);
      self.error('');
      registry.accounts.getAccounts().then(function (rows) {
        if (current !== requestId) return;
        self.accounts(format.asList(rows));
        self.lastUpdated(format.formatDateTime(new Date()));
      }).catch(function (error) {
        if (current !== requestId) return;
        self.error(error.message || 'Unable to load accounts.');
        self.accounts([]);
      }).finally(function () {
        if (current === requestId) self.loading(false);
      });
    };

    self.connected = function () {
      accUtils.announce('Accounts page loaded.', 'polite');
      document.title = 'Accounts | Internet Banking';
      self.refreshAccounts();
    };
    self.disconnected = function () { requestId += 1; };
  }

  return AccountsViewModel;
});
