define([], function () {
  'use strict';

  var STORAGE_KEY = 'nb.session.v1';

  function decodeBase64Url(value) {
    var padded = String(value).replace(/-/g, '+').replace(/_/g, '/');
    while (padded.length % 4) {
      padded += '=';
    }
    var binary;
    if (typeof atob === 'function') {
      binary = atob(padded);
    } else {
      binary = Buffer.from(padded, 'base64').toString('binary');
    }
    var encoded = '';
    for (var index = 0; index < binary.length; index += 1) {
      encoded += '%' + ('00' + binary.charCodeAt(index).toString(16)).slice(-2);
    }
    return decodeURIComponent(encoded);
  }

  function readExpiry(token) {
    var parts = String(token || '').split('.');
    if (parts.length < 2 || !parts[1]) {
      return null;
    }
    try {
      var payload = JSON.parse(decodeBase64Url(parts[1]));
      return payload && typeof payload.exp === 'number' ? payload.exp * 1000 : null;
    } catch (error) {
      return null;
    }
  }

  function SessionService(options) {
    var settings = options || {};
    this.storage = settings.storage;
    this.now = settings.now || function () {
      return Date.now();
    };
    this.warningWindowMs = settings.warningWindowMs == null ? 60000 : settings.warningWindowMs;
    this.schedule = settings.schedule || function (callback, delay) {
      return setInterval(callback, delay);
    };
    this.cancel = settings.cancel || function (timer) {
      clearInterval(timer);
    };
    this._listeners = {};
    this._timer = null;
    this._warnedExpiry = null;
  }

  SessionService.prototype.on = function (event, listener) {
    if (!this._listeners[event]) {
      this._listeners[event] = [];
    }
    this._listeners[event].push(listener);
    return listener;
  };

  SessionService.prototype._emit = function (event, detail) {
    var listeners = this._listeners[event] || [];
    listeners.slice().forEach(function (listener) {
      listener(detail);
    });
  };

  SessionService.prototype.read = function () {
    var raw = this.storage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    try {
      var parsed = JSON.parse(raw);
      if (!parsed || typeof parsed.accessToken !== 'string' || typeof parsed.expiresAt !== 'number') {
        this.storage.removeItem(STORAGE_KEY);
        return null;
      }
      return {
        accessToken: parsed.accessToken,
        tokenType: typeof parsed.tokenType === 'string' ? parsed.tokenType : 'Bearer',
        userId: parsed.userId,
        username: typeof parsed.username === 'string' ? parsed.username : '',
        roles: Array.isArray(parsed.roles)
          ? parsed.roles.filter(function (role) {
            return typeof role === 'string';
          })
          : [],
        expiresAt: parsed.expiresAt
      };
    } catch (error) {
      this.storage.removeItem(STORAGE_KEY);
      return null;
    }
  };

  SessionService.prototype.current = function () {
    var record = this.read();
    if (!record) {
      return null;
    }
    if (record.expiresAt <= this.now()) {
      this.storage.removeItem(STORAGE_KEY);
      this._emit('expired');
      this._emit('change');
      return null;
    }
    return record;
  };

  SessionService.prototype.establish = function (authentication) {
    var token = authentication && authentication.accessToken;
    var expiresAt = readExpiry(token);
    if (!expiresAt) {
      throw new Error('Access token is missing an expiry.');
    }
    var roles = Array.isArray(authentication.roles) ? authentication.roles.slice() : [];
    var record = {
      accessToken: token,
      tokenType: authentication.tokenType || 'Bearer',
      userId: authentication.userId,
      username: authentication.username || '',
      roles: roles,
      expiresAt: expiresAt
    };
    this.storage.setItem(STORAGE_KEY, JSON.stringify(record));
    this._warnedExpiry = null;
    this._emit('change');
    return this.read();
  };

  SessionService.prototype.clear = function () {
    this.storage.removeItem(STORAGE_KEY);
    this._warnedExpiry = null;
    this._emit('change');
  };

  SessionService.prototype.discardUnauthorized = function () {
    if (!this.read()) {
      return;
    }
    this.storage.removeItem(STORAGE_KEY);
    this._warnedExpiry = null;
    this._emit('unauthorized');
    this._emit('change');
  };

  SessionService.prototype.authorizationHeader = function () {
    var record = this.current();
    return record ? record.tokenType + ' ' + record.accessToken : null;
  };

  SessionService.prototype.hasRole = function (role) {
    var record = this.current();
    return !!(record && record.roles.indexOf(role) !== -1);
  };

  SessionService.prototype.tick = function () {
    var record = this.read();
    if (!record) {
      return;
    }
    var remaining = record.expiresAt - this.now();
    if (remaining <= 0) {
      this.storage.removeItem(STORAGE_KEY);
      this._warnedExpiry = null;
      this._emit('expired');
      this._emit('change');
      return;
    }
    if (remaining <= this.warningWindowMs && this._warnedExpiry !== record.expiresAt) {
      this._warnedExpiry = record.expiresAt;
      this._emit('warning', remaining);
    }
  };

  SessionService.prototype.start = function (intervalMs) {
    var self = this;
    this.stop();
    this._timer = this.schedule(function () {
      self.tick();
    }, intervalMs || 1000);
  };

  SessionService.prototype.stop = function () {
    if (this._timer != null) {
      this.cancel(this._timer);
      this._timer = null;
    }
  };

  SessionService.readExpiry = readExpiry;
  SessionService.STORAGE_KEY = STORAGE_KEY;

  return SessionService;
});
