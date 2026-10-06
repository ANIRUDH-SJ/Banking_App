(function (factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  }
})(function () {
  var MOBILE = /^[0-9+][0-9 -]{7,19}$/;
  var OTP = /^[0-9]{6}$/;
  var EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  function text(value) {
    return value == null ? '' : String(value).trim();
  }

  function ValidationService() {
    this.required = function (value, message) {
      return text(value) ? '' : (message || 'Enter a value.');
    };

    this.username = function (value) {
      var raw = text(value);
      if (!raw) {
        return 'Enter a user ID.';
      }
      if (raw.length < 3 || raw.length > 100) {
        return 'Use 3 to 100 characters for the user ID.';
      }
      return '';
    };

    this.email = function (value) {
      var raw = text(value);
      if (!raw) {
        return 'Enter an email address.';
      }
      if (raw.length > 254 || !EMAIL.test(raw)) {
        return 'Enter a valid email address.';
      }
      return '';
    };

    this.usernameOrEmail = function (value) {
      var raw = text(value);
      if (!raw) {
        return 'Enter your user ID or email address.';
      }
      if (raw.length < 3 || raw.length > 254) {
        return 'Enter the user ID or email address for the account.';
      }
      return '';
    };

    this.password = function (value) {
      var raw = value == null ? '' : String(value);
      if (!raw) {
        return 'Enter a password.';
      }
      if (raw.length < 12 || raw.length > 128) {
        return 'Use 12 to 128 characters.';
      }
      return '';
    };

    this.passwordMatch = function (password, confirmation) {
      if (!confirmation) {
        return 'Enter the password again.';
      }
      return password === confirmation ? '' : 'Passwords do not match.';
    };

    this.personName = function (value, label) {
      var raw = text(value);
      if (!raw) {
        return 'Enter ' + (label || 'a name') + '.';
      }
      if (raw.length > 100) {
        return (label || 'Name') + ' must be 100 characters or fewer.';
      }
      return '';
    };

    this.dateOfBirth = function (value, today) {
      var raw = text(value).substring(0, 10);
      if (!raw) {
        return 'Enter a date of birth.';
      }
      if (!/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
        return 'Enter a valid date of birth.';
      }
      var entered = new Date(raw + 'T00:00:00');
      if (Number.isNaN(entered.getTime())) {
        return 'Enter a valid date of birth.';
      }
      var current = today ? new Date(today) : new Date();
      current.setHours(0, 0, 0, 0);
      if (entered >= current) {
        return 'Date of birth must be in the past.';
      }
      return '';
    };

    this.mobile = function (value) {
      var raw = text(value);
      if (!raw) {
        return 'Enter a mobile number.';
      }
      if (!MOBILE.test(raw)) {
        return 'Enter a mobile number using digits, spaces, or hyphens.';
      }
      return '';
    };

    this.otp = function (value) {
      var raw = text(value);
      if (!OTP.test(raw)) {
        return 'Enter the 6-digit code.';
      }
      return '';
    };
  }

  return ValidationService;
});
