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
    audit: { label: 'Audit', hint: 'Event type, for example LOGIN', sub: 'Security and operational events with their outcome.' }
  };

  function AdminViewModel() {
    var self = this;

    self.problem = new ui.Problem();
    self.isLoading = ko.observable(false);
    self.searching = ko.observable(false);
    self.view = ko.observable('users');
    self.viewItems = Object.keys(VIEWS).map(function (key) {
      return { value: key, label: VIEWS[key].label };
    });

    self.users = ko.observableArray([]);
    self.accounts = ko.observableArray([]);
    self.transactions = ko.observableArray([]);
    self.auditEvents = ko.observableArray([]);
    self.userTotal = ko.observable(0);
    self.accountTotal = ko.observable(0);
    self.transactionTotal = ko.observable(0);
    self.auditTotal = ko.observable(0);
    self.userQuery = ko.observable('');
    self.accountQuery = ko.observable('');
    self.transactionQuery = ko.observable('');
    self.auditQuery = ko.observable('');

    var queries = { users: self.userQuery, accounts: self.accountQuery, transactions: self.transactionQuery, audit: self.auditQuery };
    var rows = { users: self.users, accounts: self.accounts, transactions: self.transactions, audit: self.auditEvents };

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
    self.loadAudit = function () {
      return load(function () { return AdminService.listAuditEvents({ eventType: self.auditQuery(), page: 0, size: 10 }); }, self.auditEvents, self.auditTotal);
    };

    var loaders = { users: self.loadUsers, accounts: self.loadAccounts, transactions: self.loadTransactions, audit: self.loadAudit };

    self.search = function () {
      if (self.searching()) {
        return false;
      }
      self.searching(true);
      self.problem.clear();
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
      return Promise.all([self.loadUsers(), self.loadAccounts(), self.loadTransactions(), self.loadAudit()]).finally(function () {
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
