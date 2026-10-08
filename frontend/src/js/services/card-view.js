define(['knockout', './registry', './format'], function (ko, registry, format) {
  var NETWORKS = { VISA: 'Visa', MASTERCARD: 'Mastercard', RUPAY: 'RuPay' };

  function lastFour(card) {
    if (card.lastFour) {
      return String(card.lastFour);
    }
    var digits = String(card.maskedCardNumber || '').replace(/[^0-9]/g, '');
    return digits.slice(-4);
  }

  function grouped(number) {
    return String(number || '').replace(/[^0-9]/g, '').replace(/(\d{4})(?=\d)/g, '$1 ');
  }

  /**
   * One card on screen. Each card reveals its own number on request and
   * masks it again when the bank's reveal window ends or the page closes.
   */
  function CardView(card) {
    var self = this;
    var timer = null;
    var ticket = 0;

    self.card = card;
    self.id = card.cardId;
    self.isCredit = String(card.cardType || '').toUpperCase() === 'CREDIT';
    self.network = NETWORKS[String(card.cardNetwork || '').toUpperCase()] || format.labelize(card.cardNetwork);
    self.typeLabel = format.labelize(card.cardType) + ' card';
    self.last4 = lastFour(card);
    self.expiry = card.expiryMonth && card.expiryYear
      ? String(card.expiryMonth).padStart(2, '0') + '/' + String(card.expiryYear).slice(-2)
      : '—';
    self.canReveal = card.revealable === true && ['CLOSED', 'EXPIRED'].indexOf(String(card.status).toUpperCase()) < 0;

    self.number = ko.observable('');
    self.secondsLeft = ko.observable(0);
    self.revealing = ko.observable(false);
    self.error = ko.observable('');
    self.shown = ko.pureComputed(function () {
      return !!self.number();
    });
    self.display = ko.pureComputed(function () {
      return self.number() || '•••• •••• •••• ' + self.last4;
    });
    self.toggleLabel = ko.pureComputed(function () {
      if (!self.canReveal) {
        return 'Full number unavailable for card ending ' + self.last4;
      }
      if (self.revealing()) {
        return 'Showing card number';
      }
      return (self.shown() ? 'Hide' : 'Show') + ' number of card ending ' + self.last4;
    });

    function stop() {
      if (timer) {
        window.clearInterval(timer);
        timer = null;
      }
    }

    self.hide = function () {
      ticket += 1;
      stop();
      self.number('');
      self.secondsLeft(0);
      self.revealing(false);
    };

    self.reveal = function () {
      if (self.revealing() || !self.canReveal) {
        return;
      }
      var mine = ++ticket;
      self.revealing(true);
      self.error('');
      registry.cards.reveal(self.id).then(function (details) {
        if (mine !== ticket) {
          return;
        }
        var digits = String(details && details.cardNumber || '').replace(/\s/g, '');
        if (!/^[0-9]{12,19}$/.test(digits) || !digits.endsWith(self.last4))
          throw new Error('The full card number is unavailable for this card.');
        var until = Date.now() + Math.max(5, Number(details && details.revealSeconds) || 30) * 1000;
        self.number(grouped(digits));
        self.secondsLeft(Math.ceil((until - Date.now()) / 1000));
        stop();
        timer = window.setInterval(function () {
          var left = Math.ceil((until - Date.now()) / 1000);
          if (left <= 0) {
            self.hide();
          } else {
            self.secondsLeft(left);
          }
        }, 1000);
      }).catch(function (error) {
        if (mine === ticket) {
          self.error((error && error.message) || 'The card number could not be shown.');
        }
      }).finally(function () {
        if (mine === ticket) {
          self.revealing(false);
        }
      });
    };

    self.toggle = function () {
      if (self.shown()) {
        self.hide();
      } else {
        self.reveal();
      }
    };

    self.dispose = self.hide;
  }

  CardView.wrap = function (cards) {
    return format.asList(cards).map(function (card) {
      return new CardView(card);
    });
  };

  return CardView;
});
