(function (factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  }
})(function () {
  function asList(value) {
    if (Array.isArray(value)) {
      return value;
    }
    if (value && Array.isArray(value.content)) {
      return value.content;
    }
    return [];
  }

  function formatMoney(amount, currencyCode) {
    var currency = currencyCode || 'INR';
    var numeric = typeof amount === 'number' ? amount : Number(amount);
    if (!isFinite(numeric)) {
      return (amount == null ? '' : String(amount)) + (currency ? ' ' + currency : '');
    }
    var locale = currency === 'INR' ? 'en-IN' : 'en-GB';
    try {
      return new Intl.NumberFormat(locale, {
        style: 'currency',
        currency: currency,
        minimumFractionDigits: 2,
        maximumFractionDigits: 4
      }).format(numeric);
    } catch (ignore) {
      return numeric.toFixed(2) + ' ' + currency;
    }
  }

  function formatDate(value) {
    if (!value) {
      return '';
    }
    var raw = String(value);
    var date = raw.length === 10 ? new Date(raw + 'T00:00:00') : new Date(raw);
    if (Number.isNaN(date.getTime())) {
      return raw;
    }
    return new Intl.DateTimeFormat('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric'
    }).format(date);
  }

  function formatDateTime(value) {
    if (!value) {
      return '';
    }
    var date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return String(value);
    }
    return new Intl.DateTimeFormat('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    }).format(date);
  }

  function formatTime(epochMs) {
    return new Intl.DateTimeFormat('en-IN', {
      hour: 'numeric',
      minute: '2-digit'
    }).format(new Date(epochMs));
  }

  function maskAccount(accountNumber) {
    var raw = String(accountNumber || '').replace(/\s/g, '');
    if (raw.length <= 4) {
      return raw;
    }
    return '•••• ' + raw.slice(-4);
  }

  function labelize(value) {
    if (!value) {
      return '';
    }
    var text = String(value).toLowerCase().replace(/_/g, ' ');
    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  return {
    asList: asList,
    formatMoney: formatMoney,
    formatDate: formatDate,
    formatDateTime: formatDateTime,
    formatTime: formatTime,
    maskAccount: maskAccount,
    labelize: labelize
  };
});
