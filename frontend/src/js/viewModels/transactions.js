define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/format',
  '../services/member2-style',
  'oj-c/button'
], function (ko, accUtils, registry, format) {
  function TransactionsViewModel() {
    var self = this;
    var requestId = 0;
    self.accounts = ko.observableArray([]);
    self.selectedAccountId = ko.observable('');
    self.searchText = ko.observable('');
    self.fromDate = ko.observable('');
    self.toDate = ko.observable('');
    self.message = ko.observable('');
    self.error = ko.observable('');
    self.loading = ko.observable(false);
    self.exporting = ko.observable(false);
    self.transactions = ko.observableArray([]);
    self.money = format.formatMoney;
    self.dateTime = format.formatDateTime;
    self.accountLabel = function (account) {
      return format.labelize(account.accountType) + ' · ' + format.maskAccount(account.accountNumber);
    };
    self.filteredTransactions = ko.pureComputed(function () {
      var term = self.searchText().trim().toLowerCase();
      if (!term) return self.transactions();
      return self.transactions().filter(function (item) {
        return [item.reference, item.narration, item.type, item.status]
          .join(' ').toLowerCase().indexOf(term) !== -1;
      });
    });

    function query() {
      var params = new URLSearchParams({ page: '0', size: '50' });
      if (self.fromDate()) params.set('from', self.fromDate());
      if (self.toDate()) params.set('to', self.toDate());
      return '?' + params.toString();
    }

    function valid() {
      if (!self.selectedAccountId()) {
        self.error('Choose an account.');
        return false;
      }
      if (self.fromDate() && self.toDate() && self.fromDate() > self.toDate()) {
        self.error('Choose a To date that is on or after the From date.');
        return false;
      }
      return true;
    }

    self.applyFilters = function () {
      if (!valid()) return false;
      var current = ++requestId;
      self.loading(true);
      self.error('');
      self.message('');
      registry.accounts.getStatement(self.selectedAccountId(), query()).then(function (page) {
        if (current !== requestId) return;
        self.transactions(format.asList(page));
        self.message('Showing up to 50 entries. Export CSV for the complete statement.');
      }).catch(function (error) {
        if (current !== requestId) return;
        self.error(error.message || 'Unable to load transactions.');
        self.transactions([]);
      }).finally(function () {
        if (current === requestId) self.loading(false);
      });
      return false;
    };

    self.exportStatement = function () {
      if (!valid() || self.exporting()) return;
      self.exporting(true);
      self.error('');
      var params = new URLSearchParams();
      if (self.fromDate()) params.set('from', self.fromDate());
      if (self.toDate()) params.set('to', self.toDate());
      var path = '/api/v1/accounts/' + encodeURIComponent(self.selectedAccountId()) + '/statement.csv';
      if (params.toString()) path += '?' + params.toString();
      registry.apiClient.get(path).then(function (csv) {
        var url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
        var link = document.createElement('a');
        link.href = url;
        link.download = 'statement-' + self.selectedAccountId() + '.csv';
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(function () { URL.revokeObjectURL(url); }, 0);
        self.message('Statement downloaded.');
      }).catch(function (error) {
        self.error(error.message || 'Unable to export the statement.');
      }).finally(function () { self.exporting(false); });
    };

    self.connected = function () {
      accUtils.announce('Transaction history page loaded.', 'polite');
      document.title = 'Transactions | Internet Banking';
      self.loading(true);
      registry.accounts.getAccounts().then(function (rows) {
        self.accounts(format.asList(rows));
        if (self.accounts().length) {
          self.selectedAccountId(self.accounts()[0].accountId);
          self.applyFilters();
        } else {
          self.message('No accounts are available.');
          self.loading(false);
        }
      }).catch(function (error) {
        self.error(error.message || 'Unable to load accounts.');
        self.loading(false);
      });
    };
    self.disconnected = function () { requestId += 1; };
  }
  return TransactionsViewModel;
});
