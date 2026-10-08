define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/format',
  '../services/ui-support',
  '../services/card-view',
  'oj-c/button',
  'oj-c/badge',
  'oj-c/skeleton'
], function (ko, accUtils, registry, format, ui, CardView) {
  function describe(error, fallback) {
    var message = (error && error.message) || fallback;
    if (error && error.correlationId) {
      message += ' Reference ' + error.correlationId + '.';
    }
    return message;
  }

  function dayKey(value) {
    var date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return String(value || '');
    }
    return date.getFullYear() + '-' + (date.getMonth() + 1) + '-' + date.getDate();
  }

  function dayLabel(value) {
    var date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return '';
    }
    var today = new Date();
    var yesterday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1);
    if (dayKey(date) === dayKey(today)) {
      return 'Today';
    }
    if (dayKey(date) === dayKey(yesterday)) {
      return 'Yesterday';
    }
    return new Intl.DateTimeFormat('en-IN', { weekday: 'short', day: 'numeric', month: 'short' }).format(date);
  }

  function DashboardViewModel() {
    var self = this;
    var generation = 0;
    self.greeting = ko.observable('');
    self.customerNumber = ko.observable('');
    self.today = new Intl.DateTimeFormat('en-IN', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric'
    }).format(new Date());
    self.updatedAt = ko.observable('');
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
    self.noticesLoading = ko.observable(true);
    self.noticesError = ko.observable('');
    self.notices = ko.observableArray([]);

    self.money = format.formatMoney;
    self.moneyParts = format.formatMoneyParts;
    self.when = format.formatDateTime;
    self.day = format.formatDate;
    self.mask = format.maskAccount;
    self.label = format.labelize;
    self.hasAccounts = ko.pureComputed(function () {
      return self.accounts().length > 0;
    });
    self.skeletonRows = [1, 2, 3, 4];

    self.activity = ko.pureComputed(function () {
      var groups = [];
      var byKey = {};
      self.transactions().forEach(function (entry) {
        var key = dayKey(entry.postedAt);
        if (!byKey[key]) {
          byKey[key] = { label: dayLabel(entry.postedAt), entries: [] };
          groups.push(byKey[key]);
        }
        byKey[key].entries.push(entry);
      });
      return groups;
    });

    self.signedAmount = function (entry) {
      var text = format.formatMoney(entry.amount, entry.currencyCode);
      return (entry.entryType === 'CREDIT' ? '+' : '−') + text;
    };
    self.timeOf = function (value) {
      var date = new Date(value);
      return Number.isNaN(date.getTime()) ? '' : format.formatTime(date.getTime());
    };
    self.accountTitle = ui.accountName;
    self.accountKind = ui.accountKind;
    self.tileCss = function (account) {
      var css = { 'is-ink': true };
      css['is-tone-' + ui.accountTone(account)] = true;
      return css;
    };
    self.hasNickname = function (account) {
      return !!String(account.nickname || '').trim();
    };
    self.grouped = function (number) {
      return String(number || '').replace(/(\d{4})(?=\d)/g, '$1 ');
    };
    self.statusVariant = ui.statusVariant;
    self.canTransfer = function (account) {
      return (account.currencyCode || 'INR') === 'INR' && account.accountStatus === 'ACTIVE';
    };
    self.credit = function (view) {
      return view.card.credit || null;
    };
    self.creditUsed = function (view) {
      var credit = view.card.credit;
      if (!credit || !Number(credit.creditLimit)) {
        return 0;
      }
      return Math.min(100, Math.max(0, Math.round(Number(credit.outstandingBalance || 0) / Number(credit.creditLimit) * 100)));
    };

    function go(path) {
      return function () {
        registry.go(path);
        return false;
      };
    }
    self.openAccounts = go('accounts');
    self.openTransactions = go('transactions');
    self.openCards = go('cards');
    self.openLoans = go('loans');
    self.openTransfer = go('transfer');
    self.openPayments = go('bill-payments');
    self.openBeneficiaries = go('beneficiaries');
    self.openNotices = go('notifications');

    self.openStatementFor = function (account) {
      ui.hand('transactions.accountId', account.accountId);
      registry.go('transactions');
      return false;
    };
    self.openTransferFor = function (account) {
      ui.hand('transfer.sourceAccountId', account.accountId);
      registry.go('transfer');
      return false;
    };
    self.openCard = function (view) {
      ui.hand('cards.cardId', view.id);
      registry.go('cards');
      return false;
    };
    self.pinAction = function (card) {
      var status = String(card.status || '').toUpperCase();
      if (status !== 'ACTIVE' && status !== 'INACTIVE') {
        return '';
      }
      if (card.pinLockedUntil && new Date(card.pinLockedUntil).getTime() > Date.now()) {
        return '';
      }
      return card.pinSet ? 'Change PIN' : 'Set PIN';
    };
    self.openPin = function (view) {
      ui.hand('cards.pinId', view.id);
      registry.go('cards');
      return false;
    };
    self.openNotice = function (notice) {
      ui.hand('notifications.id', notice.notificationId);
      registry.go('notifications');
      return false;
    };

    function loadTransactions(account, ticket) {
      self.transactionsLoading(true);
      self.transactionsError('');
      self.transactionAccount(ui.accountName(account) + ' ' + format.maskAccount(account.accountNumber));
      registry.transactions.list(account.accountId, '?page=0&size=6').then(function (page) {
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

    function track(promise, ticket, list, loading, error, fallback) {
      return promise.then(function (rows) {
        if (ticket === generation) {
          list(format.asList(rows));
        }
      }).catch(function (problem) {
        if (ticket === generation) {
          list([]);
          error(describe(problem, fallback));
        }
      }).finally(function () {
        if (ticket === generation) {
          loading(false);
        }
      });
    }

    self.connected = function () {
      var ticket = ++generation;
      accUtils.announce('Overview. Account balances are loaded from the bank.', 'polite');
      document.title = 'Overview | ORACLE INTERNATIONAL BANK (OIB)';
      [self.accountsLoading, self.cardsLoading, self.loansLoading, self.noticesLoading].forEach(function (flag) {
        flag(true);
      });
      [self.accountsError, self.cardsError, self.loansError, self.noticesError].forEach(function (message) {
        message('');
      });
      self.transactions([]);
      self.transactionAccount('');

      var hour = new Date().getHours();
      var hello = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
      self.greeting(hello);
      registry.profile.get().then(function (profile) {
        if (ticket !== generation || !profile) {
          return;
        }
        self.greeting(profile.firstName ? hello + ', ' + profile.firstName : hello);
        self.customerNumber(profile.customerNumber || '');
      }).catch(function () {
        if (ticket !== generation) {
          return;
        }
        var session = registry.session.getSession();
        self.greeting(session && session.username ? hello + ', ' + session.username : hello);
      });

      registry.accounts.getAccounts().then(function (rows) {
        if (ticket !== generation) {
          return;
        }
        var accounts = format.asList(rows);
        self.accounts(accounts);
        self.updatedAt(format.formatTime(Date.now()));
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

      disposeCards();
      track(registry.cards.list().then(CardView.wrap), ticket, self.cards, self.cardsLoading, self.cardsError, 'Cards could not be loaded.');
      track(registry.loans.list(), ticket, self.loans, self.loansLoading, self.loansError, 'Loans could not be loaded.');
      track(registry.notifications.list(0, 3), ticket, self.notices, self.noticesLoading, self.noticesError, 'Notices could not be loaded.');
    };

    function disposeCards() {
      self.cards().forEach(function (view) {
        view.dispose();
      });
    }

    self.disconnected = function () {
      generation += 1;
      disposeCards();
    };
  }

  return DashboardViewModel;
});
