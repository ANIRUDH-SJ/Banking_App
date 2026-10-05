define(['knockout', '../accUtils', 'ojs/ojbutton'], function (ko, accUtils) {
  function TransactionsViewModel() {
    var self = this;
    self.accountOptions = ko.observableArray([
      { id: '1', label: 'Savings Account · •••• 1001' },
      { id: '2', label: 'Current Account · •••• 2001' }
    ]);
    self.selectedAccountId = ko.observable('1');
    self.searchText = ko.observable('');
    self.fromDate = ko.observable('');
    self.toDate = ko.observable('');
    self.message = ko.observable('Showing sample transactions until the shared API client is connected.');
    self.transactions = ko.observableArray([
      { postedAt: '05 Oct 2026', reference: 'TXN-10001', narration: 'Salary credit', type: 'DEPOSIT', amount: '+ ₹ 45,000.00', status: 'COMPLETED' },
      { postedAt: '03 Oct 2026', reference: 'TXN-10002', narration: 'Electricity payment', type: 'TRANSFER', amount: '- ₹ 1,250.00', status: 'COMPLETED' },
      { postedAt: '01 Oct 2026', reference: 'TXN-10003', narration: 'ATM withdrawal', type: 'WITHDRAWAL', amount: '- ₹ 2,000.00', status: 'COMPLETED' }
    ]);
    self.filteredTransactions = ko.pureComputed(function () {
      var term = self.searchText().trim().toLowerCase();
      if (!term) return self.transactions();
      return self.transactions().filter(function (item) {
        return (item.reference + ' ' + item.narration + ' ' + item.type).toLowerCase().indexOf(term) !== -1;
      });
    });
    self.applyFilters = function () {
      if (self.fromDate() && self.toDate() && self.fromDate() > self.toDate()) {
        self.message('Choose a To date that is on or after the From date.');
        return false;
      }
      self.message('Filters applied.');
      return false;
    };
    self.exportStatement = function () {
      self.message('Statement export is ready to connect to the shared authenticated API client.');
    };
    self.connected = function () {
      accUtils.announce('Transaction history page loaded.', 'assertive');
      document.title = 'Transaction History';
    };
  }
  return TransactionsViewModel;
});
