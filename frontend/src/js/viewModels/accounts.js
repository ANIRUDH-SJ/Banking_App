define([
  'knockout',
  '../accUtils',
  'ojs/ojbutton'
], function (ko, accUtils) {
  function AccountsViewModel() {
    var self = this;

    self.accountStatus = ko.observable('Sample account data is displayed.');
    self.lastUpdated = ko.observable(self.formatDateTime(new Date()));

    self.accounts = ko.observableArray([
      {
        accountName: 'Savings Account',
        accountNumber: '•••• 1001',
        balance: '₹ 25,000.00',
        status: 'ACTIVE'
      },
      {
        accountName: 'Current Account',
        accountNumber: '•••• 2001',
        balance: '₹ 10,500.00',
        status: 'ACTIVE'
      }
    ]);

    self.refreshAccounts = function () {
      self.accountStatus(
        'Account summary refreshed. Backend data will be connected through the shared API client.'
      );
      self.lastUpdated(self.formatDateTime(new Date()));
    };

    self.connected = function () {
      accUtils.announce('Accounts page loaded.', 'assertive');
      document.title = 'My Accounts';
    };

    self.disconnected = function () {};

    self.transitionCompleted = function () {};
  }

  AccountsViewModel.prototype.formatDateTime = function (date) {
    return new Intl.DateTimeFormat('en-IN', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(date);
  };

  return AccountsViewModel;
});
