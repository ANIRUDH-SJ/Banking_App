define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/format'
], function (ko, accUtils, registry, format) {
  function describe(error, fallback) {
    var message = (error && error.message) || fallback;
    if (error && error.correlationId) {
      message += ' Reference ' + error.correlationId + '.';
    }
    return message;
  }

  function DashboardViewModel() {
    var self = this;
    var generation = 0;
    self.greeting = ko.observable('');
    self.customerNumber = ko.observable('');
    self.accountsLoading = ko.observable(true);
    self.accountsError = ko.observable('');
    self.accounts = ko.observableArray([]);
    self.transactionsLoading = ko.observable(false);
    self.transactionsError = ko.observable('');
    self.transactions = ko.observableArray([]);
    self.transactionAccount = ko.observable('');
    self.cardsLoading = ko.observable(true);
    self.cardsError = ko.observable('');
    self.cards = ko.observableArray([]);
    self.loansLoading = ko.observable(true);
    self.loansError = ko.observable('');
    self.loans = ko.observableArray([]);

    self.money = format.formatMoney;
    self.when = format.formatDateTime;
    self.day = format.formatDate;
    self.mask = format.maskAccount;
    self.label = format.labelize;
    self.hasAccounts = ko.pureComputed(function () {
      return self.accounts().length > 0;
    });

    self.openAccounts = function () {
      registry.go('accounts');
      return false;
    };
    self.openCards = function () {
      registry.go('cards');
      return false;
    };
    self.openLoans = function () {
      registry.go('loans');
      return false;
    };
    self.openTransfer = function () {
      registry.go('transfer');
      return false;
    };
    self.openPayments = function () {
      registry.go('bill-payments');
      return false;
    };
    self.openNotices = function () {
      registry.go('notifications');
      return false;
    };
    self.statusClass = function (status) {
      var value = String(status || '').toUpperCase();
      if (value === 'ACTIVE' || value === 'OPEN' || value === 'CURRENT') {
        return 'is-good';
      }
      if (value === 'CLOSED' || value === 'BLOCKED' || value === 'FROZEN' || value === 'DORMANT') {
        return 'is-bad';
      }
      return '';
    };

    function loadTransactions(account, ticket) {
      self.transactionsLoading(true);
      self.transactionsError('');
      self.transactionAccount(format.maskAccount(account.accountNumber));
      registry.transactions.list(account.accountId, '?page=0&size=5').then(function (page) {
        if (ticket !== generation) {
          return;
        }
        self.transactions(format.asList(page));
      }).catch(function (error) {
        if (ticket !== generation) {
          return;
        }
        self.transactions([]);
        self.transactionsError(describe(error, 'Recent transactions could not be loaded.'));
      }).finally(function () {
        if (ticket === generation) {
          self.transactionsLoading(false);
        }
      });
    }

    self.connected = function () {
      var ticket = ++generation;
      accUtils.announce('Home. Account balances are loaded from the bank.', 'polite');
      document.title = 'Home | Internet Banking';
      self.accountsLoading(true);
      self.cardsLoading(true);
      self.loansLoading(true);
      self.accountsError('');
      self.cardsError('');
      self.loansError('');
      self.transactions([]);
      self.transactionAccount('');

      registry.profile.get().then(function (profile) {
        if (ticket !== generation || !profile) {
          return;
        }
        var name = [profile.firstName, profile.lastName].filter(Boolean).join(' ');
        var hour = new Date().getHours();
        var hello = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
        self.greeting(name ? hello + ', ' + profile.firstName + '.' : hello + '.');
        self.customerNumber(profile.customerNumber || '');
      }).catch(function () {
        if (ticket !== generation) {
          return;
        }
        var session = registry.session.getSession();
        self.greeting(session && session.username ? session.username : '');
      });

      registry.accounts.getAccounts().then(function (rows) {
        if (ticket !== generation) {
          return;
        }
        var accounts = format.asList(rows);
        self.accounts(accounts);
        if (accounts.length) {
          loadTransactions(accounts[0], ticket);
        } else {
          self.transactionsLoading(false);
        }
      }).catch(function (error) {
        if (ticket !== generation) {
          return;
        }
        self.accounts([]);
        self.accountsError(describe(error, 'Accounts could not be loaded.'));
        self.transactionsLoading(false);
      }).finally(function () {
        if (ticket === generation) {
          self.accountsLoading(false);
        }
      });

      registry.cards.list().then(function (rows) {
        if (ticket !== generation) {
          return;
        }
        self.cards(format.asList(rows));
      }).catch(function (error) {
        if (ticket !== generation) {
          return;
        }
        self.cards([]);
        self.cardsError(describe(error, 'Cards could not be loaded.'));
      }).finally(function () {
        if (ticket === generation) {
          self.cardsLoading(false);
        }
      });

      registry.loans.list().then(function (rows) {
        if (ticket !== generation) {
          return;
        }
        self.loans(format.asList(rows));
      }).catch(function (error) {
        if (ticket !== generation) {
          return;
        }
        self.loans([]);
        self.loansError(describe(error, 'Loans could not be loaded.'));
      }).finally(function () {
        if (ticket === generation) {
          self.loansLoading(false);
        }
      });
    };

    self.disconnected = function () {
      generation += 1;
    };
  }

  return DashboardViewModel;
});
