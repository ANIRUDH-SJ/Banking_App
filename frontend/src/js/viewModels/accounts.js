define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/format',
  '../services/ui-support',
  'oj-c/button',
  'oj-c/buttonset-single',
  'oj-c/input-text',
  'oj-c/badge',
  'oj-c/skeleton'
], function (ko, accUtils, registry, format, ui) {
  var WALLETS = [
    { value: 'USD', label: 'US dollar', symbol: '$' },
    { value: 'EUR', label: 'Euro', symbol: '€' },
    { value: 'GBP', label: 'Pound sterling', symbol: '£' },
    { value: 'JPY', label: 'Japanese yen', symbol: '¥' },
    { value: 'AUD', label: 'Australian dollar', symbol: 'A$' },
    { value: 'CAD', label: 'Canadian dollar', symbol: 'C$' },
    { value: 'SGD', label: 'Singapore dollar', symbol: 'S$' }
  ];
  var NICKNAME_MAX = 40;

  function AccountsViewModel() {
    var self = this;
    var generation = 0;
    var revealTimers = {};

    self.problem = new ui.Problem();
    self.success = ko.observable('');
    self.loading = ko.observable(true);
    self.busy = ko.observable(false);
    self.accounts = ko.observableArray([]);
    self.revealedAccountIds = ko.observable({});
    self.walletOpen = ko.observable(false);
    self.walletCurrency = ko.observable(null);
    self.editingId = ko.observable(null);
    self.nickname = ko.observable('');
    self.nicknameMessages = ko.observableArray([]);

    self.inrTotal = ko.pureComputed(function () {
      return self.accounts().filter(function (account) {
        return (account.currencyCode || 'INR') === 'INR' && account.accountStatus === 'ACTIVE';
      }).reduce(function (sum, account) {
        return sum + Number(account.availableBalance || 0);
      }, 0);
    });

    self.activeCount = ko.pureComputed(function () {
      return self.accounts().filter(function (account) { return account.accountStatus === 'ACTIVE'; }).length;
    });

    self.walletCount = ko.pureComputed(function () {
      return self.accounts().filter(function (account) { return (account.currencyCode || 'INR') !== 'INR'; }).length;
    });

    self.availableWallets = ko.pureComputed(function () {
      var held = self.accounts().map(function (account) { return account.currencyCode; });
      return WALLETS.filter(function (wallet) { return held.indexOf(wallet.value) < 0; });
    });

    self.walletItems = ko.pureComputed(function () {
      return self.availableWallets().map(function (wallet) {
        return { value: wallet.value, label: wallet.value + ' · ' + wallet.label };
      });
    });

    self.label = format.labelize;
    self.money = format.formatMoney;
    self.statusVariant = ui.statusVariant;
    self.moneyParts = format.formatMoneyParts;

    self.accountTitle = ui.accountName;
    self.accountKind = ui.accountKind;
    self.tileCss = function (account) {
      var css = { 'is-ink': true, 'is-editing': self.isEditing(account) };
      css['is-tone-' + ui.accountTone(account)] = true;
      return css;
    };
    self.hasNickname = function (account) {
      return !!String(account.nickname || '').trim();
    };

    self.grouped = function (number) {
      return String(number || '').replace(/(\d{4})(?=\d)/g, '$1 ');
    };

    self.isNumberShown = function (account) {
      return !!self.revealedAccountIds()[account.accountId];
    };

    function hideNumber(id) {
      var shown = Object.assign({}, self.revealedAccountIds());
      delete shown[id];
      self.revealedAccountIds(shown);
      if (revealTimers[id]) {
        window.clearTimeout(revealTimers[id]);
        delete revealTimers[id];
      }
    }

    function hideAllNumbers() {
      Object.keys(revealTimers).forEach(function (id) { window.clearTimeout(revealTimers[id]); });
      revealTimers = {};
      self.revealedAccountIds({});
    }

    self.displayNumber = function (account) {
      return self.isNumberShown(account)
        ? self.grouped(account.accountNumber)
        : format.maskAccount(account.accountNumber);
    };

    self.toggleNumber = function (account) {
      var id = account.accountId;
      if (self.isNumberShown(account)) {
        hideNumber(id);
      } else {
        var shown = Object.assign({}, self.revealedAccountIds());
        shown[id] = true;
        self.revealedAccountIds(shown);
        revealTimers[id] = window.setTimeout(function () { hideNumber(id); }, 30000);
      }
      return false;
    };

    self.openStatement = function (account) {
      ui.hand('transactions.accountId', account.accountId);
      registry.go('transactions');
      return false;
    };

    self.openTransfer = function (account) {
      if (account && account.accountId) {
        ui.hand('transfer.sourceAccountId', account.accountId);
      }
      registry.go('transfer');
      return false;
    };

    self.isEditing = function (account) {
      return self.editingId() === account.accountId;
    };

    self.startRename = function (account) {
      self.success('');
      self.problem.clear();
      self.nickname(account.nickname || '');
      self.nicknameMessages([]);
      self.editingId(account.accountId);
      window.setTimeout(function () {
        var input = document.querySelector('.nb-rename input');
        if (input) {
          input.focus();
          input.select();
        }
      }, 50);
      return false;
    };

    self.cancelRename = function () {
      self.editingId(null);
      self.nicknameMessages([]);
    };

    self.saveRename = function (account) {
      var value = String(self.nickname() || '').trim().replace(/\s+/g, ' ');
      var error = value.length > NICKNAME_MAX ? 'Keep the nickname to ' + NICKNAME_MAX + ' characters.' : '';
      self.nicknameMessages(ui.messages(error));
      if (error || self.busy()) {
        return false;
      }
      self.busy(true);
      registry.accounts.rename(account.accountId, value).then(function (updated) {
        var merged = Object.assign({}, account, updated || {}, { nickname: (updated && 'nickname' in updated) ? updated.nickname : (value || null) });
        self.accounts.replace(account, merged);
        self.editingId(null);
        self.success(value ? 'This account is now called ' + value + '. Its account number has not changed.' : 'The nickname was removed.');
        accUtils.announce('Nickname saved.', 'polite');
      }).catch(function (failure) {
        self.nicknameMessages(ui.messages(ui.fieldMessage(failure, 'nickname') || (failure && failure.message) || 'The nickname could not be saved.'));
      }).finally(function () {
        self.busy(false);
      });
      return false;
    };

    self.openForex = function () {
      registry.go('forex');
      return false;
    };

    self.toggleWallet = function () {
      self.walletOpen(!self.walletOpen());
      self.success('');
      if (!self.walletCurrency() && self.availableWallets().length) {
        self.walletCurrency(self.availableWallets()[0].value);
      }
    };

    self.openWallet = function () {
      var currency = self.walletCurrency();
      if (!currency || self.busy()) {
        return;
      }
      self.busy(true);
      self.problem.clear();
      registry.accounts.openForeignCurrency(currency).then(function (account) {
        self.accounts.push(account);
        self.walletOpen(false);
        self.walletCurrency(null);
        self.success('Your ' + currency + ' wallet is open. Fund it from Forex.');
        accUtils.announce(currency + ' wallet opened.', 'polite');
      }).catch(function (error) {
        self.problem.set(error, 'The wallet could not be opened.');
      }).finally(function () {
        self.busy(false);
      });
    };

    self.refreshAccounts = function () {
      var ticket = ++generation;
      hideAllNumbers();
      self.loading(true);
      self.problem.clear();
      registry.accounts.getAccounts().then(function (accounts) {
        if (ticket === generation) {
          self.accounts(format.asList(accounts));
        }
      }).catch(function (error) {
        if (ticket === generation) {
          self.problem.set(error, 'Your accounts could not be loaded.');
        }
      }).finally(function () {
        if (ticket === generation) {
          self.loading(false);
        }
      });
    };

    self.connected = function () {
      accUtils.announce('Accounts.', 'polite');
      document.title = 'Accounts | Internet Banking';
      self.refreshAccounts();
    };

    self.disconnected = function () {
      generation += 1;
      hideAllNumbers();
    };
  }

  return AccountsViewModel;
});
