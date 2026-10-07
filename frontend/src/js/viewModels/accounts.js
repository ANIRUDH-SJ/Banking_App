define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/format',
  '../services/ui-support',
  'oj-c/button',
  'oj-c/buttonset-single',
  'oj-c/badge',
  'oj-c/skeleton'
], function (ko, accUtils, registry, format, ui) {
  var WALLETS = [
    { value: 'USD', label: 'US dollar', symbol: '$' },
    { value: 'EUR', label: 'Euro', symbol: '€' },
    { value: 'GBP', label: 'Pound sterling', symbol: '£' }
  ];

  function AccountsViewModel() {
    var self = this;
    var generation = 0;

    self.problem = new ui.Problem();
    self.success = ko.observable('');
    self.loading = ko.observable(true);
    self.busy = ko.observable(false);
    self.accounts = ko.observableArray([]);
    self.walletOpen = ko.observable(false);
    self.walletCurrency = ko.observable(null);

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

    self.accountTitle = function (account) {
      if ((account.currencyCode || 'INR') !== 'INR') {
        return account.currencyCode + ' wallet';
      }
      return format.labelize(account.accountType) + ' account';
    };

    self.grouped = function (number) {
      return String(number || '').replace(/(\d{4})(?=\d)/g, '$1 ');
    };

    self.openStatement = function (account) {
      ui.hand('transactions.accountId', account.accountId);
      registry.go('transactions');
      return false;
    };

    self.openTransfer = function () {
      registry.go('transfer');
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
    };
  }

  return AccountsViewModel;
});
