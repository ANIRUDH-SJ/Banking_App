define(['knockout', 'ojs/ojarraydataprovider', '../services/AdminService', '../services/member3-style', '../accUtils', 'ojs/ojinputtext', 'ojs/ojprogress-circle'],
  function (ko, ArrayDataProvider, AdminService, member3Style, accUtils) {
    'use strict';

    function AdminViewModel() {
      this.users = ko.observableArray([]);
      this.accounts = ko.observableArray([]);
      this.transactions = ko.observableArray([]);
      this.auditEvents = ko.observableArray([]);
      this.usersProvider = new ArrayDataProvider(this.users, { keyAttributes: 'userId' });
      this.accountsProvider = new ArrayDataProvider(this.accounts, { keyAttributes: 'accountId' });
      this.transactionsProvider = new ArrayDataProvider(this.transactions, { keyAttributes: 'transactionId' });
      this.auditProvider = new ArrayDataProvider(this.auditEvents, { keyAttributes: 'auditEventId' });

      this.userTotal = ko.observable(0);
      this.accountTotal = ko.observable(0);
      this.transactionTotal = ko.observable(0);
      this.auditTotal = ko.observable(0);
      this.userQuery = ko.observable('');
      this.accountQuery = ko.observable('');
      this.transactionQuery = ko.observable('');
      this.auditQuery = ko.observable('');
      this.isLoading = ko.observable(false);
      this.errorMessage = ko.observable('');

      this.dateTime = (value) => value ? new Date(value).toLocaleString() : '—';
      this.joinValues = (values) => Array.isArray(values) && values.length ? values.join(', ') : '—';
      this.money = (value, currency) => new Intl.NumberFormat('en-IN', {
        style: 'currency', currency: currency || 'INR'
      }).format(Number(value || 0));

      this.reportError = (error) => {
        this.errorMessage(error.status === 403
          ? 'Administrator access is required to open this dashboard.'
          : (error.message || 'Unable to load administrator data.'));
      };

      this.loadUsers = async () => {
        try {
          const page = await AdminService.listUsers({ query: this.userQuery(), page: 0, size: 10 });
          this.users(page.content || []); this.userTotal(page.totalElements || 0);
        } catch (error) { this.reportError(error); }
      };
      this.loadAccounts = async () => {
        try {
          const page = await AdminService.listAccounts({ query: this.accountQuery(), page: 0, size: 10 });
          this.accounts(page.content || []); this.accountTotal(page.totalElements || 0);
        } catch (error) { this.reportError(error); }
      };
      this.loadTransactions = async () => {
        try {
          const page = await AdminService.listTransactions({ reference: this.transactionQuery(), page: 0, size: 10 });
          this.transactions(page.content || []); this.transactionTotal(page.totalElements || 0);
        } catch (error) { this.reportError(error); }
      };
      this.loadAudit = async () => {
        try {
          const page = await AdminService.listAuditEvents({ eventType: this.auditQuery(), page: 0, size: 10 });
          this.auditEvents(page.content || []); this.auditTotal(page.totalElements || 0);
        } catch (error) { this.reportError(error); }
      };

      this.refresh = async () => {
        this.isLoading(true); this.errorMessage('');
        try {
          await Promise.all([this.loadUsers(), this.loadAccounts(), this.loadTransactions(), this.loadAudit()]);
        } finally { this.isLoading(false); }
      };

      this.connected = () => {
        document.title = 'Administration';
        accUtils.announce('Administration page loaded.', 'polite');
        this.refresh();
      };
    }
    return AdminViewModel;
  });
