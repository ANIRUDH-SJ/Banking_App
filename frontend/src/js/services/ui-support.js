define(['knockout', 'ojs/ojarraydataprovider', 'ojs/ojconverter-number', './format', './api-error'], function (ko, ArrayDataProvider, NumberConverter, format, apiError) {
  var amountConverter = new NumberConverter.IntlNumberConverter({
    style: 'decimal',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
    useGrouping: true
  });

  function messages(text) {
    return text ? [{ severity: 'error', summary: text, detail: text }] : [];
  }

  /** Form-level error text plus the gateway correlation reference, if any. */
  function Problem() {
    this.text = ko.observable('');
    this.reference = ko.observable('');
  }

  Problem.prototype.set = function (error, fallback) {
    this.text((error && error.message) || fallback || 'Something went wrong. Try again.');
    this.reference(error && error.correlationId ? 'Reference ' + error.correlationId : '');
  };

  Problem.prototype.show = function (text) {
    this.text(text || '');
    this.reference('');
  };

  Problem.prototype.clear = function () {
    this.text('');
    this.reference('');
  };

  function fieldMessage(error, field) {
    return apiError.fieldMessage ? apiError.fieldMessage(error, field) : '';
  }

  function newKey(prefix) {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
      return crypto.randomUUID();
    }
    return (prefix || 'op') + '-' + Date.now().toString(36) + Math.random().toString(36).slice(2);
  }

  function options(rows) {
    return new ArrayDataProvider(rows, { keyAttributes: 'value' });
  }

  /** The kind of account, such as "Savings account" or "USD wallet". */
  function accountKind(account) {
    if (!account) {
      return 'Account';
    }
    if ((account.currencyCode || 'INR') !== 'INR') {
      return account.currencyCode + ' wallet';
    }
    var type = format.labelize(account.accountType);
    return type ? type + ' account' : 'Account';
  }

  /** The customer's nickname when set, otherwise the kind of account. */
  function accountName(account) {
    var nickname = account && String(account.nickname || '').trim();
    return nickname || accountKind(account);
  }

  function accountOption(account) {
    var nickname = String(account.nickname || '').trim();
    return {
      value: account.accountId,
      label: (nickname ? nickname + ' · ' : '') + format.labelize(account.accountType) + ' ' +
        format.maskAccount(account.accountNumber) + ' · ' + format.formatMoney(account.availableBalance, account.currencyCode)
    };
  }

  /** Saves a downloaded file without leaving the page. */
  function saveFile(blob, filename) {
    var link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    window.setTimeout(function () {
      URL.revokeObjectURL(link.href);
      link.remove();
    }, 0);
  }

  function parseAmount(value) {
    if (value === null || value === undefined || value === '') {
      return NaN;
    }
    return typeof value === 'number' ? value : Number(String(value).replace(/,/g, ''));
  }

  function amountError(value, min, max, currency) {
    var amount = parseAmount(value);
    if (!Number.isFinite(amount) || amount <= 0) {
      return 'Enter an amount.';
    }
    if (Math.round(amount * 100) / 100 !== amount) {
      return 'Use at most two decimal places.';
    }
    if (min !== undefined && min !== null && amount < Number(min)) {
      return 'The minimum is ' + format.formatMoney(min, currency || 'INR') + '.';
    }
    if (max !== undefined && max !== null && amount > Number(max)) {
      return 'The maximum is ' + format.formatMoney(max, currency || 'INR') + '.';
    }
    return '';
  }

  var handoff = {};

  /** Passes a selection to the next screen; it is read once. */
  function hand(key, value) {
    handoff[key] = value;
  }

  function take(key) {
    var value = handoff[key];
    delete handoff[key];
    return value;
  }

  /** Step position for the details, review, confirm and receipt sequence. */
  function Flow(names) {
    var self = this;
    self.names = names;
    self.at = ko.observable(0);
    self.stateOf = function (index) {
      var at = self.at();
      return index === at ? 'is-current' : (index < at ? 'is-done' : '');
    };
    self.is = function (index) {
      return self.at() === index;
    };
    self.go = function (index) {
      self.at(index);
      if (typeof document === 'undefined' || !document.querySelectorAll || typeof setTimeout !== 'function') {
        return;
      }
      setTimeout(function () {
        var headings = document.querySelectorAll('.nb-flow-panel h2[tabindex]');
        for (var i = 0; i < headings.length; i += 1) {
          if (headings[i].offsetParent !== null) {
            headings[i].focus();
            return;
          }
        }
      }, 0);
    };
  }

  function statusVariant(status) {
    var value = String(status || '').toUpperCase();
    if (['ACTIVE', 'COMPLETED', 'OPEN', 'PAID_OFF', 'MATURED', 'PAID_OUT'].indexOf(value) >= 0) {
      return 'successSubtle';
    }
    if (['BLOCKED', 'LOCKED', 'FAILED', 'CLOSED', 'FROZEN', 'REVERSED', 'OVERDUE', 'DISABLED', 'INACTIVE'].indexOf(value) >= 0) {
      return 'dangerSubtle';
    }
    if (['PENDING', 'AWAITING_OTP', 'AUTHORIZED', 'PROCESSING', 'PENDING_ACTIVATION'].indexOf(value) >= 0) {
      return 'warningSubtle';
    }
    return 'neutralSubtle';
  }

  return {
    messages: messages,
    Problem: Problem,
    Flow: Flow,
    amountConverter: amountConverter,
    hand: hand,
    take: take,
    fieldMessage: fieldMessage,
    newKey: newKey,
    options: options,
    accountOption: accountOption,
    accountKind: accountKind,
    accountName: accountName,
    saveFile: saveFile,
    parseAmount: parseAmount,
    amountError: amountError,
    statusVariant: statusVariant
  };
});
