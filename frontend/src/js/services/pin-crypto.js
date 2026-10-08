(function (factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  }
})(function () {
  var WEAK = ['0123', '1234', '2345', '3456', '4567', '5678', '6789', '9876', '8765', '7654',
    '6543', '5432', '4321', '3210', '1212', '1004', '2580'];

  function formatError(pin) {
    return /^[0-9]{4}$/.test(String(pin || '')) ? '' : 'Enter the four-digit PIN.';
  }

  function strengthError(pin) {
    var value = String(pin || '');
    var shape = formatError(value);
    if (shape) {
      return shape;
    }
    if (/^(\d)\1{3}$/.test(value) || WEAK.indexOf(value) >= 0) {
      return 'Avoid repeated or sequential digits such as 1111 or 1234.';
    }
    return '';
  }

  function decode(base64) {
    var binary = atob(base64);
    var bytes = new Uint8Array(binary.length);
    for (var i = 0; i < binary.length; i += 1) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes.buffer;
  }

  function encode(buffer) {
    var bytes = new Uint8Array(buffer);
    var binary = '';
    for (var i = 0; i < bytes.length; i += 1) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  }

  /**
   * Encrypts PINs on the device with the bank's RSA-OAEP key, so a PIN only
   * ever travels as ciphertext with a freshness timestamp inside it.
   */
  function PinSealer(cards, subtle, now) {
    var crypto = subtle || (typeof window !== 'undefined' && window.crypto && window.crypto.subtle) || null;
    var clock = now || function () { return Date.now(); };

    this.available = function () {
      return !!crypto;
    };

    /** Resolves to { pinKeyId, sealed: [ciphertext per PIN] }; null entries stay null. */
    this.seal = function (pins) {
      if (!crypto) {
        return Promise.reject(new Error('This browser cannot encrypt a PIN. Use a current version of Chrome, Edge, Firefox or Safari.'));
      }
      return cards.pinKey().then(function (key) {
        return crypto.importKey('spki', decode(key.publicKey), { name: 'RSA-OAEP', hash: 'SHA-256' }, false, ['encrypt'])
          .then(function (publicKey) {
            return Promise.all(pins.map(function (pin) {
              if (pin === null || pin === undefined) {
                return null;
              }
              var payload = new TextEncoder().encode(JSON.stringify({ pin: String(pin), issuedAt: clock() }));
              return crypto.encrypt({ name: 'RSA-OAEP' }, publicKey, payload).then(encode);
            }));
          })
          .then(function (sealed) {
            return { pinKeyId: key.keyId, sealed: sealed };
          });
      });
    };
  }

  return {
    PinSealer: PinSealer,
    formatError: formatError,
    strengthError: strengthError
  };
});
