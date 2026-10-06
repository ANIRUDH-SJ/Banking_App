(function (factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  }
})(function () {
  var STORAGE_KEY = 'internet-banking.session';

  function decodePayload(token) {
    if (!token || typeof token !== 'string' || token.split('.').length < 2) {
      return null;
    }
    try {
      var segment = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      while (segment.length % 4) {
        segment += '=';
      }
      var binary = atob(segment);
      var json = decodeURIComponent(Array.prototype.map.call(binary, function (char) {
        return '%' + ('00' + char.charCodeAt(0).toString(16)).slice(-2);
      }).join(''));
      return JSON.parse(json);
    } catch (ignore) {
      return null;
    }
  }

  function SessionService(storage, clock) {
    this.storage = storage;
    this.now = clock || function () { return Date.now(); };
  }

  SessionService.prototype.save = function (authentication) {
    if (!authentication || !authentication.accessToken) {
      return null;
    }
    var payload = decodePayload(authentication.accessToken);
    if (!payload || typeof payload.exp !== 'number') {
      return null;
    }
    var record = {
      accessToken: authentication.accessToken,
      tokenType: authentication.tokenType || 'Bearer',
      userId: authentication.userId,
      username: authentication.username,
      roles: Array.isArray(authentication.roles) ? authentication.roles.slice() : [],
      expiresAt: payload.exp * 1000
    };
    this.storage.setItem(STORAGE_KEY, JSON.stringify(record));
    return this.getSession();
  };

  SessionService.prototype.getSession = function () {
    var raw = this.storage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    try {
      var session = JSON.parse(raw);
      if (!session || !session.accessToken || typeof session.expiresAt !== 'number' || session.expiresAt <= this.now()) {
        this.storage.removeItem(STORAGE_KEY);
        return null;
      }
      return {
        accessToken: session.accessToken,
        tokenType: session.tokenType || 'Bearer',
        userId: session.userId,
        username: session.username,
        roles: Array.isArray(session.roles) ? session.roles.slice() : [],
        expiresAt: session.expiresAt
      };
    } catch (ignore) {
      this.storage.removeItem(STORAGE_KEY);
      return null;
    }
  };

  SessionService.prototype.getAccessToken = function () {
    var session = this.getSession();
    return session ? session.accessToken : null;
  };

  SessionService.prototype.isAuthenticated = function () {
    return !!this.getAccessToken();
  };

  SessionService.prototype.hasRole = function (role) {
    var session = this.getSession();
    return !!(session && session.roles.indexOf(role) >= 0);
  };

  SessionService.prototype.clear = function () {
    this.storage.removeItem(STORAGE_KEY);
  };

  SessionService.STORAGE_KEY = STORAGE_KEY;
  SessionService.decodePayload = decodePayload;

  return SessionService;
});
