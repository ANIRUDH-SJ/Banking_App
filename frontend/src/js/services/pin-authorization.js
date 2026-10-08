define(['knockout', './registry', './format', './pin-crypto'], function (ko, registry, format, pinCrypto) {
  /**
   * Lets a payment be confirmed with the PIN of the debit card linked to
   * the paying account instead of a one-time code.
   */
  function PinAuthorization(accountId) {
    var self = this;
    var sealer = new pinCrypto.PinSealer(registry.cards);

    self.cards = ko.observableArray([]);
    self.method = ko.observable('OTP');
    self.pin = ko.observable('');
    self.messages = ko.observableArray([]);

    self.card = ko.pureComputed(function () {
      var id = ko.unwrap(accountId);
      return self.cards().filter(function (card) {
        return card.accountId === id && String(card.cardType).toUpperCase() === 'DEBIT' && card.status === 'ACTIVE';
      })[0] || null;
    });

    self.lockedUntil = ko.pureComputed(function () {
      var card = self.card();
      var until = card && card.pinLockedUntil ? new Date(card.pinLockedUntil) : null;
      return until && until.getTime() > Date.now() ? until : null;
    });

    self.available = ko.pureComputed(function () {
      return !!self.card() && !!self.card().pinSet && !self.lockedUntil() && sealer.available();
    });

    self.usingPin = ko.pureComputed(function () {
      return self.method() === 'PIN';
    });

    self.cardLabel = ko.pureComputed(function () {
      var card = self.card();
      return card ? 'Debit card ending ' + String(card.lastFour || String(card.maskedCardNumber || '').slice(-4)) : '';
    });

    self.lockText = ko.pureComputed(function () {
      var until = self.lockedUntil();
      return until ? 'Your card PIN is locked until ' + format.formatTime(until.getTime()) + ' after too many wrong attempts. Use a one-time code instead.' : '';
    });

    self.load = function () {
      return registry.cards.list().then(function (cards) {
        self.cards(format.asList(cards));
      }).catch(function () {
        self.cards([]);
      });
    };

    self.use = function (method) {
      self.method(method === 'PIN' && self.available() ? 'PIN' : 'OTP');
      self.pin('');
      self.messages([]);
    };

    self.reset = function () {
      self.use('OTP');
    };

    self.validate = function () {
      var error = pinCrypto.formatError(self.pin());
      self.messages(error ? [{ severity: 'error', summary: error, detail: error }] : []);
      return !error;
    };

    /** Resolves to the cardPin body field; the PIN itself never leaves the browser unencrypted. */
    self.authorization = function () {
      var card = self.card();
      return sealer.seal([String(self.pin())]).then(function (result) {
        return { cardId: card.cardId, pinKeyId: result.pinKeyId, encryptedPin: result.sealed[0] };
      });
    };

    /** Shows a rejected PIN on the field, and falls back to a one-time code once the PIN locks. */
    self.rejected = function (error) {
      var code = error && error.code;
      self.pin('');
      if (code && String(code).indexOf('PIN_') === 0) {
        var text = (error && error.message) || 'The PIN was not accepted.';
        self.messages([{ severity: 'error', summary: text, detail: text }]);
      }
      if (code === 'PIN_LOCKED' || code === 'PIN_NOT_SET') {
        return self.load();
      }
      return Promise.resolve();
    };
  }

  return PinAuthorization;
});
