define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/format',
  '../services/ui-support',
  '../services/CardService',
  '../services/card-view',
  '../services/pin-crypto',
  'oj-c/button',
  'oj-c/badge',
  'oj-c/checkbox',
  'oj-c/input-sensitive-text',
  'oj-c/skeleton'
], function (ko, accUtils, registry, format, ui, cardService, CardView, pinCrypto) {
  var ACTIONS = {
    INACTIVE: { action: 'ACTIVATE', label: 'Activate card', chroming: 'callToAction', confirm: 'Activate this card? It can be used for payments straight away.', done: 'Card activated.' },
    ACTIVE: { action: 'BLOCK', label: 'Block card', chroming: 'danger', confirm: 'Block this card? Payments and withdrawals will be declined until you unblock it.', done: 'Card blocked.' },
    BLOCKED: { action: 'UNBLOCK', label: 'Unblock card', chroming: 'outlined', confirm: 'Unblock this card? It will work for payments again.', done: 'Card unblocked.' }
  };
  var DAY = 24 * 60 * 60 * 1000;

  function startOfDay(value) {
    var date = value instanceof Date ? value : new Date(String(value) + (String(value).length === 10 ? 'T00:00:00' : ''));
    return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
  }

  function CardsViewModel() {
    var self = this;
    var generation = 0;
    var detailTicket = 0;
    var sealer = new pinCrypto.PinSealer(registry.cards);

    self.problem = new ui.Problem();
    self.success = ko.observable('');
    self.loading = ko.observable(true);
    self.busy = ko.observable(false);
    self.cards = ko.observableArray([]);
    self.accounts = ko.observableArray([]);
    self.confirming = ko.observable(null);
    self.focusId = ko.observable(null);

    self.label = format.labelize;
    self.money = format.formatMoney;
    self.day = format.formatDate;
    self.when = format.formatDateTime;
    self.statusVariant = ui.statusVariant;

    self.actionFor = function (view) {
      return ACTIONS[String(view.card.status || '').toUpperCase()] || null;
    };

    self.linked = function (view) {
      var account = self.accounts().filter(function (item) { return item.accountId === view.card.accountId; })[0];
      return account ? ui.accountName(account) + ' ' + format.maskAccount(account.accountNumber) : '';
    };

    self.meta = function (view) {
      var linked = self.linked(view);
      return view.network + (linked ? ' · linked to ' + linked : '');
    };

    self.pinLockedUntil = function (view) {
      var until = view.card.pinLockedUntil ? new Date(view.card.pinLockedUntil) : null;
      return until && until.getTime() > Date.now() ? until : null;
    };

    self.pinStatus = function (view) {
      var locked = self.pinLockedUntil(view);
      if (locked) {
        return 'Locked until ' + format.formatTime(locked.getTime());
      }
      return view.card.pinSet ? 'Set' : 'Not set';
    };

    self.used = function (credit) {
      if (!credit || !Number(credit.creditLimit)) {
        return 0;
      }
      return Math.min(100, Math.max(0, Math.round(Number(credit.outstandingBalance || 0) / Number(credit.creditLimit) * 1000) / 10));
    };

    self.dueIn = function (credit) {
      if (!credit || !credit.paymentDueDate) {
        return '';
      }
      var days = Math.round((startOfDay(credit.paymentDueDate) - startOfDay(new Date())) / DAY);
      if (days < 0) {
        return 'Overdue by ' + (-days) + (days === -1 ? ' day' : ' days');
      }
      return days === 0 ? 'Due today' : 'Due in ' + days + (days === 1 ? ' day' : ' days');
    };

    function replaceCard(view, updated) {
      view.dispose();
      var fresh = new CardView(Object.assign({}, view.card, updated || {}));
      self.cards.replace(view, fresh);
      if (self.detail() && self.detail().id === fresh.id) {
        self.detail(fresh);
      }
      return fresh;
    }

    /* Card status */

    self.ask = function (view) {
      self.success('');
      self.problem.clear();
      self.pinFor(null);
      self.confirming(view.id);
    };

    self.cancel = function () {
      self.confirming(null);
    };

    self.apply = function (view) {
      var plan = self.actionFor(view);
      if (!plan || self.busy()) {
        return;
      }
      self.busy(true);
      cardService.changeStatus(view.id, plan.action).then(function (updated) {
        replaceCard(view, updated);
        self.confirming(null);
        self.success(plan.done);
        accUtils.announce(plan.done, 'polite');
      }).catch(function (error) {
        self.problem.set(error, 'The card could not be updated.');
      }).finally(function () {
        self.busy(false);
      });
    };

    /* PIN set and change */

    self.pinFor = ko.observable(null);
    self.pinForm = {
      current: ko.observable(''),
      next: ko.observable(''),
      confirm: ko.observable('')
    };
    self.pinMessages = {
      current: ko.observableArray([]),
      next: ko.observableArray([]),
      confirm: ko.observableArray([])
    };

    function clearPinForm() {
      Object.keys(self.pinForm).forEach(function (key) {
        self.pinForm[key]('');
        self.pinMessages[key]([]);
      });
    }

    self.pinLabel = function (view) {
      return view.card.pinSet ? 'Change PIN' : 'Set PIN';
    };

    self.canSetPin = function (view) {
      return ['ACTIVE', 'INACTIVE'].indexOf(String(view.card.status).toUpperCase()) >= 0 && !self.pinLockedUntil(view);
    };

    self.openPin = function (view) {
      if (!view || !self.canSetPin(view)) {
        return;
      }
      self.success('');
      self.problem.clear();
      self.confirming(null);
      clearPinForm();
      self.pinFor(view.id);
      window.setTimeout(function () {
        var input = document.querySelector('.nb-pin-form input');
        if (input) {
          input.focus();
        }
      }, 50);
    };

    self.closePin = function () {
      self.pinFor(null);
      clearPinForm();
    };

    self.savePin = function (view) {
      if (!view || self.pinFor() !== view.id || !self.canSetPin(view)) {
        return false;
      }
      var changing = !!view.card.pinSet;
      var form = self.pinForm;
      var errors = {
        current: changing ? pinCrypto.formatError(form.current()) : '',
        next: pinCrypto.strengthError(form.next()),
        confirm: ''
      };
      if (!errors.next && form.confirm() !== form.next()) {
        errors.confirm = 'The PINs do not match.';
      }
      if (!errors.next && changing && form.current() === form.next()) {
        errors.next = 'Choose a PIN that is different from the current one.';
      }
      Object.keys(errors).forEach(function (key) {
        self.pinMessages[key](ui.messages(errors[key]));
      });
      if (errors.current || errors.next || errors.confirm || self.busy()) {
        return false;
      }
      self.busy(true);
      self.problem.clear();
      sealer.seal([form.next(), changing ? form.current() : null]).then(function (result) {
        return registry.cards.setPin(view.id, {
          pinKeyId: result.pinKeyId,
          encryptedPin: result.sealed[0],
          encryptedCurrentPin: result.sealed[1]
        });
      }).then(function (updated) {
        replaceCard(view, Object.assign({ pinSet: true, pinLockedUntil: null }, updated || {}));
        self.closePin();
        var done = changing ? 'Your PIN was changed.' : 'Your PIN is set. You can use it at ATMs, in shops and to confirm online payments.';
        self.success(done);
        accUtils.announce(done, 'polite');
      }).catch(function (error) {
        var fields = (error && error.fieldErrors) || {};
        form.current('');
        self.pinMessages.current(ui.messages(fields.currentPin || fields.encryptedCurrentPin || ''));
        self.pinMessages.next(ui.messages(fields.newPin || fields.encryptedPin || ''));
        if (error && error.code === 'PIN_LOCKED') {
          self.closePin();
          refresh();
        }
        if (!fields.currentPin && !fields.encryptedCurrentPin && !fields.newPin && !fields.encryptedPin) {
          self.problem.set(error, 'The PIN could not be saved.');
        }
      }).finally(function () {
        self.busy(false);
      });
      return false;
    };

    /* Credit card details */

    self.detail = ko.observable(null);
    self.detailLoading = ko.observable(false);
    self.detailError = ko.observable('');
    self.transactions = ko.observableArray([]);
    self.plans = ko.observableArray([]);

    self.credit = ko.pureComputed(function () {
      return self.detail() ? self.detail().card.credit || null : null;
    });

    self.unbilled = ko.pureComputed(function () {
      return self.transactions().filter(function (row) { return !row.billed && row.status !== 'PENDING'; })
        .reduce(function (sum, row) { return sum + (inbound(row) ? -1 : 1) * Math.abs(Number(row.amount || 0)); }, 0);
    });

    self.cycle = ko.pureComputed(function () {
      var credit = self.credit();
      if (!credit || !credit.statementDate) {
        return '';
      }
      var statement = new Date(String(credit.statementDate) + 'T00:00:00');
      var grace = credit.paymentDueDate ? Math.round((startOfDay(credit.paymentDueDate) - startOfDay(statement)) / DAY) : null;
      return 'Statement on the ' + ordinal(statement.getDate()) + ' of each month' + (grace ? ', payment due ' + grace + ' days later' : '') + '.';
    });

    function ordinal(n) {
      var rest = n % 100;
      var suffix = rest >= 11 && rest <= 13 ? 'th' : ({ 1: 'st', 2: 'nd', 3: 'rd' }[n % 10] || 'th');
      return n + suffix;
    }

    function inbound(row) {
      return ['REFUND', 'PAYMENT', 'CREDIT'].indexOf(String(row.type).toUpperCase()) >= 0;
    }

    self.inbound = inbound;
    self.signed = function (row) {
      var text = format.formatMoney(Math.abs(Number(row.amount || 0)), row.currencyCode || 'INR');
      return inbound(row) ? '+' + text : text;
    };

    self.rowState = function (row) {
      if (row.emiPlan) {
        return { label: 'EMI · ' + row.emiPlan.tenureMonths + ' months', variant: 'infoSubtle' };
      }
      if (row.status === 'PENDING') {
        return { label: 'Pending', variant: 'warningSubtle' };
      }
      return row.billed ? { label: 'Billed', variant: 'neutralSubtle' } : { label: 'Unbilled', variant: 'neutralSubtle' };
    };

    function loadDetail(view) {
      var ticket = ++detailTicket;
      self.detailLoading(true);
      self.detailError('');
      self.transactions([]);
      self.plans([]);
      Promise.all([registry.cards.transactions(view.id, 0, 50), registry.cards.emiPlans(view.id)]).then(function (results) {
        if (ticket !== detailTicket) {
          return;
        }
        self.transactions(format.asList(results[0]));
        self.plans(format.asList(results[1]));
      }).catch(function (error) {
        if (ticket === detailTicket) {
          self.detailError((error && error.message) || 'Card transactions could not be loaded.');
        }
      }).finally(function () {
        if (ticket === detailTicket) {
          self.detailLoading(false);
        }
      });
    }

    self.openDetail = function (view) {
      self.success('');
      self.problem.clear();
      self.closeEmi();
      self.detail(view);
      loadDetail(view);
      window.scrollTo(0, 0);
      window.setTimeout(function () {
        var heading = document.getElementById('card-detail-heading');
        if (heading) {
          heading.focus();
        }
      }, 0);
    };

    self.closeDetail = function () {
      detailTicket += 1;
      var view = self.detail();
      self.closeEmi();
      self.detail(null);
      if (view) {
        self.focusId(view.id);
      }
      return false;
    };

    /* EMI conversion */

    self.emiRow = ko.observable(null);
    self.emiOptions = ko.observableArray([]);
    self.emiLoading = ko.observable(false);
    self.emiTenure = ko.observable(null);
    self.emiStep = ko.observable('choose');
    self.emiConsent = ko.observable(false);
    self.emiError = ko.observable('');
    var emiKey = '';

    self.emiChoice = ko.pureComputed(function () {
      var tenure = Number(self.emiTenure());
      return self.emiOptions().filter(function (option) { return option.tenureMonths === tenure; })[0] || null;
    });

    self.isEmiRow = function (row) {
      return !!self.emiRow() && self.emiRow().transactionId === row.transactionId;
    };

    self.startEmi = function (row) {
      var view = self.detail();
      if (!view) {
        return;
      }
      self.emiRow(row);
      self.emiOptions([]);
      self.emiTenure(null);
      self.emiStep('choose');
      self.emiConsent(false);
      self.emiError('');
      self.emiLoading(true);
      emiKey = ui.newKey('emi');
      registry.cards.emiOptions(view.id, row.transactionId).then(function (result) {
        if (!self.isEmiRow(row)) {
          return;
        }
        var options = format.asList(result && result.options);
        self.emiOptions(options);
        self.emiTenure(options.length ? options[Math.min(1, options.length - 1)].tenureMonths : null);
      }).catch(function (error) {
        if (self.isEmiRow(row)) {
          self.emiError((error && error.message) || 'EMI plans could not be loaded.');
        }
      }).finally(function () {
        self.emiLoading(false);
      });
    };

    self.closeEmi = function () {
      self.emiRow(null);
      self.emiOptions([]);
      self.emiError('');
    };


    self.toEmiReview = function () {
      if (!self.emiChoice()) {
        self.emiError('Choose a plan to continue.');
        return;
      }
      self.emiError('');
      self.emiConsent(false);
      self.emiStep('review');
    };

    self.backToOptions = function () {
      self.emiStep('choose');
    };

    self.confirmEmi = function () {
      var view = self.detail();
      var row = self.emiRow();
      var choice = self.emiChoice();
      if (!view || !row || !choice || self.busy()) {
        return;
      }
      if (!self.emiConsent()) {
        self.emiError('Confirm that you have read the plan terms.');
        return;
      }
      self.busy(true);
      self.emiError('');
      registry.cards.convertToEmi(view.id, row.transactionId, choice.tenureMonths, emiKey).then(function (updated) {
        self.transactions.replace(row, Object.assign({}, row, updated || {}));
        self.closeEmi();
        var done = row.merchantName + ' is now ' + choice.tenureMonths + ' monthly instalments of ' + format.formatMoney(choice.monthlyInstalment, 'INR') + '.';
        self.success(done);
        accUtils.announce(done, 'polite');
        return Promise.all([registry.cards.emiPlans(view.id), registry.cards.getById(view.id)]).then(function (results) {
          self.plans(format.asList(results[0]));
          if (results[1]) {
            replaceCard(view, results[1]);
          }
        }).catch(function () {
          return undefined;
        });
      }).catch(function (error) {
        self.emiError((error && error.message) || 'The purchase could not be converted.');
      }).finally(function () {
        self.busy(false);
      });
    };

    self.planRow = function (plan) {
      var row = self.transactions().filter(function (item) { return item.transactionId === plan.transactionId; })[0];
      return row ? row.merchantName : 'Purchase';
    };

    /* Loading */

    function disposeCards() {
      self.cards().forEach(function (view) {
        view.dispose();
      });
    }

    function refresh() {
      return cardService.listCards().then(function (cards) {
        disposeCards();
        self.cards(CardView.wrap(cards));
      }).catch(function () {
        return undefined;
      });
    }

    self.connected = function () {
      var ticket = ++generation;
      var preset = ui.take('cards.cardId');
      var pinId = ui.take('cards.pinId');
      accUtils.announce('Cards.', 'polite');
      document.title = 'Cards | Internet Banking';
      self.loading(true);
      self.problem.clear();
      self.success('');
      self.detail(null);
      self.pinFor(null);
      Promise.all([cardService.listCards(), registry.accounts.getAccounts().catch(function () { return []; })]).then(function (results) {
        if (ticket !== generation) {
          return;
        }
        disposeCards();
        self.cards(CardView.wrap(results[0]));
        self.accounts(format.asList(results[1]));
        var pinCard = self.cards().filter(function (view) { return view.id === pinId; })[0];
        var chosen = self.cards().filter(function (view) { return view.id === preset; })[0];
        if (pinCard && self.canSetPin(pinCard)) {
          self.openPin(pinCard);
          self.focusId(pinCard.id);
        } else if (chosen && chosen.isCredit) {
          self.openDetail(chosen);
        } else if (chosen) {
          self.focusId(chosen.id);
        }
      }).catch(function (error) {
        if (ticket === generation) {
          self.problem.set(error, 'Your cards could not be loaded.');
        }
      }).finally(function () {
        if (ticket === generation) {
          self.loading(false);
        }
      });
    };

    self.focusId.subscribe(function (id) {
      if (!id) {
        return;
      }
      window.setTimeout(function () {
        var node = document.getElementById('card-' + id);
        if (node) {
          node.scrollIntoView({ block: 'center', behavior: 'smooth' });
          node.focus({ preventScroll: true });
        }
      }, 60);
    });

    self.disconnected = function () {
      generation += 1;
      detailTicket += 1;
      disposeCards();
    };
  }

  return CardsViewModel;
});
