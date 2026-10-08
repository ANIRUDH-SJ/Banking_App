define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/format',
  '../services/ui-support',
  '../services/LoanService',
  'oj-c/button',
  'oj-c/input-number',
  'oj-c/select-single',
  'oj-c/badge',
  'oj-c/form-layout',
  'oj-c/skeleton'
], function (ko, accUtils, registry, format, ui, loanService) {
  function LoanCard(loan, owner) {
    var self = this;
    self.loan = ko.observable(loan);
    self.open = ko.observable(false);
    self.reviewing = ko.observable(false);
    self.busy = ko.observable(false);
    self.sourceAccountId = ko.observable(owner.defaultAccountId());
    self.amount = ko.observable(Number(loan.emiAmount) || null);
    self.accountMessages = ko.observableArray([]);
    self.amountMessages = ko.observableArray([]);
    self.problem = new ui.Problem();
    self.receipt = ko.observable(null);
    self.payments = ko.observableArray([]);
    self.idempotencyKey = '';

    self.repaid = ko.pureComputed(function () {
      var current = self.loan();
      var principal = Number(current.principalAmount);
      var outstanding = Number(current.outstandingPrincipal);
      if (!(principal > 0) || !Number.isFinite(outstanding)) {
        return 0;
      }
      return Math.max(0, Math.min(100, Math.round((1 - outstanding / principal) * 100)));
    });

    self.amountText = ko.pureComputed(function () {
      return format.formatMoney(ui.parseAmount(self.amount()), self.loan().currencyCode || 'INR');
    });

    self.toggle = function () {
      self.open(!self.open());
      self.reviewing(false);
      self.receipt(null);
      self.problem.clear();
    };

    self.review = function () {
      var current = self.loan();
      var errors = {
        account: self.sourceAccountId() ? '' : 'Choose the account to pay from.',
        amount: ui.amountError(self.amount(), 1, current.outstandingPrincipal, current.currencyCode || 'INR')
      };
      self.accountMessages(ui.messages(errors.account));
      self.amountMessages(ui.messages(errors.amount));
      if (errors.account || errors.amount) {
        return false;
      }
      self.idempotencyKey = ui.newKey('loan');
      self.reviewing(true);
      return false;
    };

    self.edit = function () {
      self.reviewing(false);
    };

    self.pay = function () {
      if (self.busy()) {
        return;
      }
      self.busy(true);
      self.problem.clear();
      loanService.makePayment(self.loan().loanId, {
        sourceAccountId: self.sourceAccountId(),
        amount: ui.parseAmount(self.amount()),
        idempotencyKey: self.idempotencyKey
      }).then(function (receipt) {
        self.receipt(receipt);
        self.reviewing(false);
        if (receipt && receipt.outstandingAfter != null) {
          self.loan(Object.assign({}, self.loan(), { outstandingPrincipal: receipt.outstandingAfter }));
        }
        accUtils.announce('Loan repayment made.', 'polite');
        self.loadPayments();
      }).catch(function (error) {
        self.problem.set(error, 'The repayment could not be made.');
      }).finally(function () {
        self.busy(false);
      });
    };

    self.loadPayments = function () {
      loanService.listPayments(self.loan().loanId, 0, 3).then(function (page) {
        self.payments(format.asList(page).slice(0, 3));
      }).catch(function () {
        self.payments([]);
      });
    };
  }

  function LoansViewModel() {
    var self = this;
    var generation = 0;

    self.problem = new ui.Problem();
    self.loading = ko.observable(true);
    self.loans = ko.observableArray([]);
    self.accounts = ko.observableArray([]);
    self.defaultAccountId = ko.observable(null);

    self.accountOptions = ko.pureComputed(function () {
      return ui.options(self.accounts().map(ui.accountOption));
    });

    self.amountConverter = ui.amountConverter;
    self.label = format.labelize;
    self.money = format.formatMoney;
    self.date = format.formatDate;
    self.statusVariant = ui.statusVariant;

    self.accountText = function (id) {
      var account = self.accounts().filter(function (item) { return item.accountId === id; })[0];
      return account ? format.labelize(account.accountType) + ' ' + format.maskAccount(account.accountNumber) : '';
    };

    self.dueSoon = function (loan) {
      if (!loan.nextDueDate) {
        return false;
      }
      var days = (new Date(loan.nextDueDate + 'T00:00:00').getTime() - Date.now()) / 86400000;
      return days <= 7;
    };

    self.connected = function () {
      var ticket = ++generation;
      accUtils.announce('Loans.', 'polite');
      document.title = 'Loans | ORACLE INTERNATIONAL BANK (OIB)';
      self.loading(true);
      self.problem.clear();
      Promise.all([loanService.listLoans(), registry.accounts.getAccounts()]).then(function (results) {
        if (ticket !== generation) {
          return;
        }
        var payable = format.asList(results[1]).filter(function (account) {
          return account.accountStatus === 'ACTIVE' && (account.currencyCode || 'INR') === 'INR';
        });
        self.accounts(payable);
        self.defaultAccountId(payable[0] ? payable[0].accountId : null);
        self.loans(format.asList(results[0]).map(function (loan) {
          var card = new LoanCard(loan, self);
          card.loadPayments();
          return card;
        }));
      }).catch(function (error) {
        if (ticket === generation) {
          self.problem.set(error, 'Your loans could not be loaded.');
        }
      }).finally(function () {
        if (ticket === generation) {
          self.loading(false);
        }
      });
    };

    self.disconnected = function () {
      generation += 1;
    };
  }

  return LoansViewModel;
});
