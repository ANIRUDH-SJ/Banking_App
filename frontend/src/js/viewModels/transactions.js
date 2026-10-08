define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/format',
  '../services/ui-support',
  'oj-c/button',
  'oj-c/select-single',
  'oj-c/input-date-text',
  'oj-c/badge',
  'oj-c/skeleton'
], function (ko, accUtils, registry, format, ui) {
  var PAGE_SIZE = 20;
  var TYPES = ['TRANSFER', 'DEPOSIT', 'WITHDRAWAL', 'LOAN_PAYMENT', 'REVERSAL'];
  var STATUSES = ['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REVERSED'];

  function choices(values) {
    return ui.options([{ value: 'ALL', label: 'All' }].concat(values.map(function (value) {
      return { value: value, label: format.labelize(value) };
    })));
  }

  function TransactionsViewModel() {
    var self = this;
    var generation = 0;
    var request = 0;

    self.problem = new ui.Problem();
    self.loading = ko.observable(true);
    self.downloading = ko.observable(false);
    self.accounts = ko.observableArray([]);
    self.accountId = ko.observable(null);
    self.from = ko.observable(null);
    self.to = ko.observable(null);
    self.type = ko.observable('ALL');
    self.status = ko.observable('ALL');
    self.rows = ko.observableArray([]);
    self.page = ko.observable(0);
    self.totalPages = ko.observable(1);
    self.totalElements = ko.observable(0);
    self.dateMessages = ko.observableArray([]);
    self.applied = ko.observable({});
    self.pdfBusy = ko.observable(false);
    self.emailState = ko.observable('idle');
    self.emailReceipt = ko.observable(null);
    self.emailError = ko.observable('');

    self.accountOptions = ko.pureComputed(function () {
      return ui.options(self.accounts().map(ui.accountOption));
    });
    self.typeOptions = choices(TYPES);
    self.statusOptions = choices(STATUSES);

    self.account = ko.pureComputed(function () {
      var id = self.accountId();
      return self.accounts().filter(function (account) { return account.accountId === id; })[0] || null;
    });

    self.label = format.labelize;
    self.accountName = ui.accountName;
    self.accountKind = ui.accountKind;
    self.mask = format.maskAccount;
    self.money = format.formatMoney;
    self.date = format.formatDate;
    self.time = function (value) {
      return value ? format.formatTime(new Date(value).getTime()) : '';
    };
    self.statusVariant = ui.statusVariant;

    self.signed = function (row) {
      var text = format.formatMoney(row.amount, row.currencyCode);
      return row.entryType === 'CREDIT' ? '+' + text : '−' + text;
    };

    self.rangeText = ko.pureComputed(function () {
      if (!self.totalElements()) {
        return '';
      }
      var start = self.page() * PAGE_SIZE + 1;
      var end = Math.min(self.totalElements(), start + self.rows().length - 1);
      return start + '–' + end + ' of ' + self.totalElements();
    });

    function filters() {
      var applied = self.applied();
      return {
        from: applied.from || null,
        to: applied.to || null,
        type: applied.type && applied.type !== 'ALL' ? applied.type : null,
        status: applied.status && applied.status !== 'ALL' ? applied.status : null
      };
    }

    function query(withPage) {
      var active = filters();
      var parts = [];
      ['from', 'to', 'type', 'status'].forEach(function (key) {
        if (active[key]) {
          parts.push(key + '=' + encodeURIComponent(active[key]));
        }
      });
      if (withPage) {
        parts.push('page=' + self.page(), 'size=' + PAGE_SIZE);
      }
      return parts.length ? '?' + parts.join('&') : '';
    }

    function snapshot() {
      self.applied({ from: self.from(), to: self.to(), type: self.type(), status: self.status() });
      self.emailState('idle');
    }

    self.filterSummary = ko.pureComputed(function () {
      var active = filters();
      var period = active.from || active.to
        ? (active.from ? format.formatDate(active.from) : 'Opening') + ' – ' + (active.to ? format.formatDate(active.to) : 'today')
        : 'All dates';
      return [period, active.type ? format.labelize(active.type) : 'All types', active.status ? format.labelize(active.status) : 'All statuses'].join(' · ');
    });

    function rangeError() {
      if (self.from() && self.to() && self.from() > self.to()) {
        return 'The start date must be on or before the end date.';
      }
      return '';
    }

    function load() {
      var id = self.accountId();
      if (!id) {
        self.rows([]);
        self.loading(false);
        return;
      }
      var ticket = ++request;
      self.loading(true);
      self.problem.clear();
      registry.accounts.getStatement(id, query(true)).then(function (result) {
        if (ticket !== request) {
          return;
        }
        self.rows(format.asList(result));
        self.totalPages(Math.max(1, (result && result.totalPages) || 1));
        self.totalElements((result && result.totalElements) || format.asList(result).length);
      }).catch(function (error) {
        if (ticket === request) {
          self.rows([]);
          self.problem.set(error, 'Transactions could not be loaded.');
        }
      }).finally(function () {
        if (ticket === request) {
          self.loading(false);
        }
      });
    }

    self.apply = function () {
      var error = rangeError();
      self.dateMessages(ui.messages(error));
      if (error) {
        return;
      }
      snapshot();
      self.page(0);
      load();
    };

    self.reset = function () {
      self.from(null);
      self.to(null);
      self.type('ALL');
      self.status('ALL');
      self.dateMessages([]);
      snapshot();
      self.page(0);
      load();
    };

    self.previous = function () {
      if (self.page() > 0) {
        self.page(self.page() - 1);
        load();
      }
    };

    self.next = function () {
      if (self.page() + 1 < self.totalPages()) {
        self.page(self.page() + 1);
        load();
      }
    };

    function last4(account) {
      return String(account.accountNumber || '').slice(-4);
    }

    self.download = function () {
      var account = self.account();
      if (!account || self.downloading()) {
        return;
      }
      self.downloading(true);
      registry.accounts.downloadStatement(account.accountId, query(false)).then(function (csv) {
        var blob = new Blob([typeof csv === 'string' ? csv : JSON.stringify(csv)], { type: 'text/csv' });
        ui.saveFile(blob, 'statement-' + last4(account) + '.csv');
        accUtils.announce('Statement downloaded.', 'polite');
      }).catch(function (error) {
        self.problem.set(error, 'The statement could not be downloaded.');
      }).finally(function () {
        self.downloading(false);
      });
    };

    self.downloadPdf = function () {
      var account = self.account();
      if (!account || self.pdfBusy()) {
        return;
      }
      self.pdfBusy(true);
      registry.accounts.downloadStatementPdf(account.accountId, query(false)).then(function (file) {
        ui.saveFile(file.blob, file.filename || 'statement-' + last4(account) + '.pdf');
        accUtils.announce('PDF statement downloaded.', 'polite');
      }).catch(function (error) {
        self.problem.set(error, 'The PDF statement could not be prepared.');
      }).finally(function () {
        self.pdfBusy(false);
      });
    };

    self.emailPdf = function () {
      var account = self.account();
      if (!account || self.emailState() === 'sending') {
        return;
      }
      self.emailState('sending');
      self.emailError('');
      self.emailReceipt(null);
      registry.accounts.emailStatement(account.accountId, filters()).then(function (receipt) {
        self.emailReceipt(receipt || {});
        self.emailState('sent');
        accUtils.announce('Statement emailed to ' + ((receipt && receipt.sentTo) || 'your registered address') + '.', 'polite');
      }).catch(function (error) {
        self.emailError((error && error.message) || 'The statement could not be emailed.');
        self.emailState('failed');
      });
    };

    self.emailLabel = ko.pureComputed(function () {
      return { sending: 'Sending…', sent: 'Email again', failed: 'Try again' }[self.emailState()] || 'Email PDF';
    });

    self.emailTitle = ko.pureComputed(function () {
      return {
        sending: 'Emailing your statement…',
        sent: 'Statement emailed',
        failed: 'Statement not sent'
      }[self.emailState()] || '';
    });

    self.emailDetail = ko.pureComputed(function () {
      if (self.emailState() === 'failed') {
        return self.emailError();
      }
      if (self.emailState() === 'sent') {
        return self.emailSummary() + ' The PDF covers ' + self.filterSummary() + '.';
      }
      return 'Preparing the PDF for ' + self.filterSummary() + '.';
    });

    self.emailSummary = ko.pureComputed(function () {
      var receipt = self.emailReceipt();
      if (self.emailState() !== 'sent' || !receipt) {
        return '';
      }
      return 'Sent to ' + (receipt.sentTo || 'your registered email') + (receipt.sentAt ? ' at ' + format.formatTime(new Date(receipt.sentAt).getTime()) : '') + '.';
    });

    var ready = false;
    self.accountId.subscribe(function () {
      self.emailState('idle');
      if (ready) {
        self.page(0);
        load();
      }
    });

    self.connected = function () {
      var ticket = ++generation;
      accUtils.announce('Transactions.', 'polite');
      document.title = 'Transactions | ORACLE INTERNATIONAL BANK (OIB)';
      var preset = ui.take('transactions.accountId');
      self.loading(true);
      if (preset) {
        self.from(null);
        self.to(null);
        self.type('ALL');
        self.status('ALL');
      }
      snapshot();
      registry.accounts.getAccounts().then(function (accounts) {
        if (ticket !== generation) {
          return;
        }
        var list = format.asList(accounts);
        self.accounts(list);
        ready = false;
        var owned = function (id) {
          return list.some(function (account) { return account.accountId === id; });
        };
        self.accountId(owned(preset) ? preset : (owned(self.accountId()) ? self.accountId() : (list[0] && list[0].accountId) || null));
        ready = true;
        load();
      }).catch(function (error) {
        if (ticket === generation) {
          self.problem.set(error, 'Your accounts could not be loaded.');
          self.loading(false);
        }
      });
    };

    self.disconnected = function () {
      ready = false;
      generation += 1;
    };
  }

  return TransactionsViewModel;
});
