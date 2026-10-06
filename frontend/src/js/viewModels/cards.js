define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/format',
  '../services/ui-support',
  '../services/CardService',
  'oj-c/button',
  'oj-c/badge',
  'oj-c/skeleton'
], function (ko, accUtils, registry, format, ui, cardService) {
  var ACTIONS = {
    INACTIVE: { action: 'ACTIVATE', label: 'Activate card', chroming: 'callToAction', confirm: 'Activate this card? It can be used for payments straight away.', done: 'Card activated.' },
    ACTIVE: { action: 'BLOCK', label: 'Block card', chroming: 'danger', confirm: 'Block this card? Payments and withdrawals will be declined until you unblock it.', done: 'Card blocked.' },
    BLOCKED: { action: 'UNBLOCK', label: 'Unblock card', chroming: 'outlined', confirm: 'Unblock this card? It will work for payments again.', done: 'Card unblocked.' }
  };

  function CardsViewModel() {
    var self = this;
    var generation = 0;

    self.problem = new ui.Problem();
    self.success = ko.observable('');
    self.loading = ko.observable(true);
    self.busy = ko.observable(false);
    self.cards = ko.observableArray([]);
    self.accounts = ko.observableArray([]);
    self.confirming = ko.observable(null);

    self.label = format.labelize;
    self.statusVariant = ui.statusVariant;

    self.actionFor = function (card) {
      return ACTIONS[String(card.status || '').toUpperCase()] || null;
    };

    self.expiry = function (card) {
      if (!card.expiryMonth || !card.expiryYear) {
        return '—';
      }
      return String(card.expiryMonth).padStart(2, '0') + '/' + String(card.expiryYear).slice(-2);
    };

    self.network = function (card) {
      var value = String(card.cardNetwork || '').toUpperCase();
      return { VISA: 'Visa', MASTERCARD: 'Mastercard', RUPAY: 'RuPay' }[value] || format.labelize(value);
    };

    self.linked = function (card) {
      var account = self.accounts().filter(function (item) { return item.accountId === card.accountId; })[0];
      return account ? format.labelize(account.accountType) + ' ' + format.maskAccount(account.accountNumber) : '—';
    };

    self.ask = function (card) {
      self.success('');
      self.problem.clear();
      self.confirming(card.cardId);
    };

    self.cancel = function () {
      self.confirming(null);
    };

    self.apply = function (card) {
      var plan = self.actionFor(card);
      if (!plan || self.busy()) {
        return;
      }
      self.busy(true);
      cardService.changeStatus(card.cardId, plan.action).then(function (updated) {
        self.cards.replace(card, updated || card);
        self.confirming(null);
        self.success(plan.done);
        accUtils.announce(plan.done, 'polite');
      }).catch(function (error) {
        self.problem.set(error, 'The card could not be updated.');
      }).finally(function () {
        self.busy(false);
      });
    };

    self.connected = function () {
      var ticket = ++generation;
      accUtils.announce('Cards.', 'polite');
      document.title = 'Cards | Internet Banking';
      self.loading(true);
      self.problem.clear();
      self.success('');
      Promise.all([cardService.listCards(), registry.accounts.getAccounts().catch(function () { return []; })]).then(function (results) {
        if (ticket === generation) {
          self.cards(format.asList(results[0]));
          self.accounts(format.asList(results[1]));
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

    self.disconnected = function () {
      generation += 1;
    };
  }

  return CardsViewModel;
});
