define([
  'knockout',
  '../accUtils',
  '../services/AdminService',
  '../services/format',
  '../services/ui-support',
  'oj-c/button',
  'oj-c/buttonset-single',
  'oj-c/input-text',
  'oj-c/badge',
  'oj-c/skeleton'
], function (ko, accUtils, AdminService, format, ui) {
  'use strict';

  var VIEWS = {
    users: { label: 'Users', hint: 'Username, email or customer name', sub: 'Sign-in status, roles and the linked customer profile.' },
    accounts: { label: 'Accounts', hint: 'Account number', sub: 'Balances, status and the customers linked to each account.' },
    transactions: { label: 'Transactions', hint: 'Transaction reference', sub: 'The latest money movement across the bank.' },
    loans: { label: 'Loans', hint: 'Loan account number', sub: 'Principal provided, outstanding exposure, instalments and due dates.' },
    audit: { label: 'Audit', hint: 'Event type, for example LOGIN', sub: 'Security and operational events with their outcome.' }
  };

  function AdminViewModel(options) {
    var self = this;
    var initialView = options && VIEWS[options.initialView] ? options.initialView : 'users';

    self.problem = new ui.Problem();
    self.isLoading = ko.observable(false);
    self.searching = ko.observable(false);
    self.updating = ko.observable('');
    self.actionMessage = ko.observable('');
    self.view = ko.observable(initialView);
    self.viewItems = Object.keys(VIEWS).map(function (key) {
      return { value: key, label: VIEWS[key].label };
    });

    self.users = ko.observableArray([]);
    self.accounts = ko.observableArray([]);
    self.transactions = ko.observableArray([]);
    self.loans = ko.observableArray([]);
    self.auditEvents = ko.observableArray([]);
    self.userTotal = ko.observable(0);
    self.accountTotal = ko.observable(0);
    self.transactionTotal = ko.observable(0);
    self.loanTotal = ko.observable(0);
    self.auditTotal = ko.observable(0);
    self.loanSummary = ko.observable({ totalLoans: 0, activeLoans: 0, currencies: [] });
    self.userQuery = ko.observable('');
    self.accountQuery = ko.observable('');
    self.transactionQuery = ko.observable('');
    self.loanQuery = ko.observable('');
    self.auditQuery = ko.observable('');

    var queries = { users: self.userQuery, accounts: self.accountQuery, transactions: self.transactionQuery, loans: self.loanQuery, audit: self.auditQuery };
    var rows = { users: self.users, accounts: self.accounts, transactions: self.transactions, loans: self.loans, audit: self.auditEvents };

    self.current = ko.pureComputed(function () {
      return VIEWS[self.view()];
    });
    self.query = ko.pureComputed({
      read: function () { return queries[self.view()](); },
      write: function (value) { queries[self.view()](value || ''); }
    });
    self.empty = ko.pureComputed(function () {
      return !self.isLoading() && !self.searching() && rows[self.view()]().length === 0;
    });

    self.label = format.labelize;
    self.money = format.formatMoney;
    self.when = function (value) {
      return value ? format.formatDateTime(value) : '—';
    };
    self.joinValues = function (values) {
      return Array.isArray(values) && values.length ? values.join(', ') : '—';
    };
    self.loanAmounts = function (field) {
      var currencies = self.loanSummary().currencies || [];
      return currencies.length ? currencies.map(function (item) {
        return self.money(item[field], item.currencyCode);
      }).join(' · ') : '—';
    };
    self.customerName = function (user) {
      var customer = user && user.customer;
      return customer ? [customer.firstName, customer.lastName].filter(Boolean).join(' ') : '—';
    };
    self.statusVariant = ui.statusVariant;
    self.outcomeVariant = function (outcome) {
      var value = String(outcome || '').toUpperCase();
      if (value === 'SUCCESS') {
        return 'successSubtle';
      }
      return value === 'FAILURE' || value === 'DENIED' ? 'dangerSubtle' : 'neutralSubtle';
    };

    function reportError(error) {
      if (error && error.status === 403) {
        self.problem.show('Administrator access is required to open this console.');
        return;
      }
      self.problem.set(error, 'Administrator data could not be loaded.');
    }

    function load(fetch, target, total) {
      return fetch().then(function (page) {
        target(format.asList(page));
        total((page && page.totalElements) || format.asList(page).length);
      }).catch(reportError);
    }

    self.loadUsers = function () {
      return load(function () { return AdminService.listUsers({ query: self.userQuery(), page: 0, size: 10 }); }, self.users, self.userTotal);
    };
    self.loadAccounts = function () {
      return load(function () { return AdminService.listAccounts({ query: self.accountQuery(), page: 0, size: 10 }); }, self.accounts, self.accountTotal);
    };
    self.loadTransactions = function () {
      return load(function () { return AdminService.listTransactions({ reference: self.transactionQuery(), page: 0, size: 10 }); }, self.transactions, self.transactionTotal);
    };
    self.loadLoans = function () {
      return load(function () { return AdminService.listLoans({ query: self.loanQuery(), page: 0, size: 10 }); }, self.loans, self.loanTotal);
    };
    self.loadLoanSummary = function () {
      return AdminService.loanSummary().then(function (summary) {
        self.loanSummary(summary || { totalLoans: 0, activeLoans: 0, currencies: [] });
        self.loanTotal((summary && summary.totalLoans) || 0);
      }).catch(reportError);
    };
    self.loadAudit = function () {
      return load(function () { return AdminService.listAuditEvents({ eventType: self.auditQuery(), page: 0, size: 10 }); }, self.auditEvents, self.auditTotal);
    };

    var loaders = { users: self.loadUsers, accounts: self.loadAccounts, transactions: self.loadTransactions, loans: self.loadLoans, audit: self.loadAudit };

    self.userActionLabel = function (user) {
      if (user.status === 'ACTIVE') {
        return 'Disable';
      }
      return user.status === 'DISABLED' || user.status === 'LOCKED' ? 'Enable' : '';
    };
    self.accountActionLabel = function (account) {
      if (account.status === 'ACTIVE') {
        return 'Freeze';
      }
      if (account.status === 'FROZEN') {
        return 'Unfreeze';
      }
      return account.status === 'PENDING' ? 'Activate' : '';
    };
    self.isUpdating = function (kind, id) {
      return self.updating() === kind + ':' + id;
    };

    function changeStatus(kind, row, nextStatus, action, request, target, idField) {
      var key = kind + ':' + row[idField];
      if (self.updating() || !window.confirm(action + ' ' + (row.username || row.accountNumber) + '?')) {
        return false;
      }
      self.problem.clear();
      self.actionMessage('');
      self.updating(key);
      request(row[idField], nextStatus).then(function (updated) {
        target.replace(row, updated);
        self.actionMessage(action + ' completed successfully.');
        self.loadAudit();
      }).catch(reportError).finally(function () {
        self.updating('');
      });
      return false;
    }

    self.changeUserStatus = function (user) {
      var nextStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
      var action = nextStatus === 'ACTIVE' ? 'Enable user' : 'Disable user';
      return changeStatus('user', user, nextStatus, action, AdminService.updateUserStatus, self.users, 'userId');
    };
    self.changeAccountStatus = function (account) {
      var nextStatus = account.status === 'ACTIVE' ? 'FROZEN' : 'ACTIVE';
      var action = nextStatus === 'FROZEN' ? 'Freeze account' : 'Activate account';
      return changeStatus('account', account, nextStatus, action, AdminService.updateAccountStatus, self.accounts, 'accountId');
    };

    self.search = function () {
      if (self.searching()) {
        return false;
      }
      self.searching(true);
      self.problem.clear();
      self.actionMessage('');
      loaders[self.view()]().finally(function () {
        self.searching(false);
      });
      return false;
    };

    self.clearSearch = function () {
      self.query('');
      self.search();
    };

    self.refresh = function () {
      self.isLoading(true);
      self.problem.clear();
      self.actionMessage('');
      return Promise.all([self.loadUsers(), self.loadAccounts(), self.loadTransactions(), self.loadLoans(), self.loadLoanSummary(), self.loadAudit()]).finally(function () {
        self.isLoading(false);
      });
    };

    self.connected = function () {
      document.title = 'Administration | ORACLE INTERNATIONAL BANK (OIB)';
      accUtils.announce('Administration console.', 'polite');
      self.refresh();
    };
  }

  return AdminViewModel;
});
