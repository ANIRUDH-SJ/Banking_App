define([], function () {
  'use strict';

  var MOBILE = /^[0-9+][0-9 -]{7,19}$/;
  var EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  var OTP = /^[0-9]{6}$/;
  var ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

  function trim(value) {
    return value == null ? '' : String(value).trim();
  }

  function result(fields) {
    var errors = {};
    Object.keys(fields).forEach(function (key) {
      if (fields[key]) {
        errors[key] = fields[key];
      }
    });
    return {
      valid: Object.keys(errors).length === 0,
      fieldErrors: errors
    };
  }

  function username(value) {
    var text = trim(value);
    if (!text) {
      return 'Enter a username.';
    }
    if (text.length < 3 || text.length > 100) {
      return 'Use 3 to 100 characters for the username.';
    }
    return '';
  }

  function email(value) {
    var text = trim(value);
    if (!text) {
      return 'Enter an email address.';
    }
    if (text.length > 254 || !EMAIL.test(text)) {
      return 'Enter a valid email address.';
    }
    return '';
  }

  function password(value) {
    var text = value == null ? '' : String(value);
    if (text.length < 12 || text.length > 128) {
      return 'Use 12 to 128 characters for the password.';
    }
    return '';
  }

  function requiredName(value, label) {
    var text = trim(value);
    if (!text) {
      return 'Enter ' + label + '.';
    }
    if (text.length > 100) {
      return label.charAt(0).toUpperCase() + label.slice(1) + ' must be 100 characters or fewer.';
    }
    return '';
  }

  function parseIsoDate(value) {
    var match = ISO_DATE.exec(trim(value));
    if (!match) {
      return null;
    }
    var year = Number(match[1]);
    var month = Number(match[2]);
    var day = Number(match[3]);
    var date = new Date(Date.UTC(year, month - 1, day));
    if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) {
      return null;
    }
    return date;
  }

  function dateOfBirth(value, now) {
    if (!trim(value)) {
      return 'Enter your date of birth.';
    }
    var date = parseIsoDate(value);
    if (!date) {
      return 'Enter the date of birth as YYYY-MM-DD.';
    }
    var current = now || new Date();
    var today = Date.UTC(current.getFullYear(), current.getMonth(), current.getDate());
    if (date.getTime() >= today) {
      return 'Date of birth must be in the past.';
    }
    return '';
  }

  function mobileNumber(value) {
    var text = value == null ? '' : String(value);
    if (!MOBILE.test(text)) {
      return 'Enter a valid mobile number.';
    }
    return '';
  }

  function usernameOrEmail(value) {
    var text = trim(value);
    if (text.length < 3 || text.length > 254) {
      return 'Enter the username or email for the account.';
    }
    return '';
  }

  function otpCode(value) {
    if (!OTP.test(trim(value))) {
      return 'Enter the 6-digit code.';
    }
    return '';
  }

  function login(values) {
    return result({
      usernameOrEmail: usernameOrEmail(values.usernameOrEmail),
      password: values.password ? '' : 'Enter your password.'
    });
  }

  function registration(values, now) {
    return result({
      username: username(values.username),
      email: email(values.email),
      password: password(values.password),
      firstName: requiredName(values.firstName, 'a first name'),
      lastName: requiredName(values.lastName, 'a last name'),
      dateOfBirth: dateOfBirth(values.dateOfBirth, now),
      mobileNumber: mobileNumber(values.mobileNumber)
    });
  }

  function profile(values) {
    return result({
      firstName: requiredName(values.firstName, 'a first name'),
      lastName: requiredName(values.lastName, 'a last name'),
      mobileNumber: mobileNumber(values.mobileNumber)
    });
  }

  function passwordResetRequest(values) {
    return result({
      usernameOrEmail: usernameOrEmail(values.usernameOrEmail)
    });
  }

  function passwordResetConfirm(values) {
    var errors = {
      code: otpCode(values.code),
      newPassword: password(values.newPassword)
    };
    if (!errors.newPassword && values.newPassword !== values.confirmPassword) {
      errors.confirmPassword = 'Enter the same password again.';
    }
    return result(errors);
  }

  return {
    username: username,
    email: email,
    password: password,
    dateOfBirth: dateOfBirth,
    mobileNumber: mobileNumber,
    usernameOrEmail: usernameOrEmail,
    otpCode: otpCode,
    login: login,
    registration: registration,
    profile: profile,
    passwordResetRequest: passwordResetRequest,
    passwordResetConfirm: passwordResetConfirm
  };
});
