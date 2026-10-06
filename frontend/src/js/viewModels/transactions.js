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

    function query(withPage) {
      var parts = [];
      if (self.from()) {
        parts.push('from=' + encodeURIComponent(self.from()));
      }
      if (self.to()) {
        parts.push('to=' + encodeURIComponent(self.to()));
      }
      if (self.type() && self.type() !== 'ALL') {
        parts.push('type=' + encodeURIComponent(self.type()));
      }
      if (self.status() && self.status() !== 'ALL') {
        parts.push('status=' + encodeURIComponent(self.status()));
      }
      if (withPage) {
        parts.push('page=' + self.page(), 'size=' + PAGE_SIZE);
      }
      return parts.length ? '?' + parts.join('&') : '';
    }

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
      self.page(0);
      load();
    };

    self.reset = function () {
      self.from(null);
      self.to(null);
      self.type('ALL');
      self.status('ALL');
      self.dateMessages([]);
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

    self.download = function () {
      var account = self.account();
      if (!account || self.downloading()) {
        return;
      }
      self.downloading(true);
      registry.accounts.downloadStatement(account.accountId, query(false)).then(function (csv) {
        var blob = new Blob([typeof csv === 'string' ? csv : JSON.stringify(csv)], { type: 'text/csv' });
        var link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = 'statement-' + String(account.accountNumber).slice(-4) + '.csv';
        document.body.appendChild(link);
        link.click();
        window.setTimeout(function () {
          URL.revokeObjectURL(link.href);
          link.remove();
        }, 0);
        accUtils.announce('Statement downloaded.', 'polite');
      }).catch(function (error) {
        self.problem.set(error, 'The statement could not be downloaded.');
      }).finally(function () {
        self.downloading(false);
      });
    };

    var ready = false;
    self.accountId.subscribe(function () {
      if (ready) {
        self.page(0);
        load();
      }
    });

    self.connected = function () {
      var ticket = ++generation;
      accUtils.announce('Transactions.', 'polite');
      document.title = 'Transactions | Internet Banking';
      var preset = ui.take('transactions.accountId');
      self.loading(true);
      registry.accounts.getAccounts().then(function (accounts) {
        if (ticket !== generation) {
          return;
        }
        var list = format.asList(accounts);
        self.accounts(list);
        ready = false;
        self.accountId(preset || self.accountId() || (list[0] && list[0].accountId) || null);
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
