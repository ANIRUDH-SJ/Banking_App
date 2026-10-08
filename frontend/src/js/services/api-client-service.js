(function (factory) {
  if (typeof define === 'function' && define.amd) {
    define(['./api-error'], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory(require('./api-error'));
  }
})(function (apiError) {
  function correlationId() {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
      return crypto.randomUUID();
    }
    return 'c' + Math.random().toString(16).slice(2) + Date.now().toString(16);
  }

  function headerValue(headers, name) {
    if (!headers || !headers.get) {
      return '';
    }
    return headers.get(name) || headers.get(name.toLowerCase()) || '';
  }

  function attachmentName(disposition) {
    var match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition || '');
    return match ? decodeURIComponent(match[1]) : '';
  }

  function ApiClientService(options) {
    var session = options.session;
    var fetchImpl = options.fetch;
    var baseUrl = options.baseUrl || '';
    var onUnauthorized = options.onUnauthorized || function () {};

    this.setUnauthorizedHandler = function (handler) {
      onUnauthorized = handler || function () {};
    };

    this.setBaseUrl = function (url) {
      baseUrl = url || '';
    };

    function request(method, path, body, requestOptions) {
      var options = requestOptions || {};
      var headers = { Accept: 'application/json, text/plain, */*' };
      var sendBody = body !== undefined && body !== null;
      if (sendBody) {
        headers['Content-Type'] = 'application/json';
      }
      if (options.headers) {
        Object.keys(options.headers).forEach(function (key) {
          headers[key] = options.headers[key];
        });
      }
      headers['X-Correlation-ID'] = correlationId();

      var authenticated = options.auth !== false;
      if (authenticated) {
        var token = session.getAccessToken();
        if (!token) {
          var missing = apiError.parseApiError(401, {
            status: 401,
            code: 'UNAUTHORIZED',
            message: 'Authentication is required.',
            path: path,
            fieldErrors: {}
          }, path, '');
          session.clear();
          if (options.redirectOnUnauthorized !== false) {
            onUnauthorized(missing);
          }
          return Promise.reject(missing);
        }
        headers.Authorization = (session.getSession() && session.getSession().tokenType ? session.getSession().tokenType : 'Bearer') + ' ' + token;
      }

      return Promise.resolve().then(function () {
        return fetchImpl(baseUrl + path, {
          method: method,
          headers: headers,
          body: sendBody ? JSON.stringify(body) : undefined,
          credentials: 'same-origin',
          cache: 'no-store'
        });
      }).then(function (response) {
        var retryAfter = headerValue(response.headers, 'Retry-After');
        var contentType = headerValue(response.headers, 'Content-Type');
        if (response.ok && options.as === 'blob') {
          return response.blob().then(function (blob) {
            return { blob: blob, filename: attachmentName(headerValue(response.headers, 'Content-Disposition')) };
          });
        }
        var reader = contentType.indexOf('application/json') >= 0
          ? response.json().catch(function () { return null; })
          : response.text();
        return reader.then(function (payload) {
          if (response.ok) {
            if (response.status === 204 || payload === '') {
              return null;
            }
            return payload;
          }
          var error = apiError.parseApiError(response.status, payload, path, retryAfter);
          if (response.status === 401 && authenticated && options.redirectOnUnauthorized !== false) {
            session.clear();
            onUnauthorized(error);
          }
          return Promise.reject(error);
        });
      }).catch(function (error) {
        if (error && error.name === 'ApiError') {
          return Promise.reject(error);
        }
        return Promise.reject(apiError.parseApiError(0, {
          status: 0,
          code: 'NETWORK',
          message: 'Internet Banking is unavailable. Check your connection and try again.',
          path: path,
          fieldErrors: {}
        }, path, ''));
      });
    }

    this.get = function (path, options) {
      return request('GET', path, null, options);
    };
    /** Resolves to { blob, filename } for file responses such as statements. */
    this.download = function (path, options) {
      return request('GET', path, null, Object.assign({}, options, { as: 'blob' }));
    };
    this.post = function (path, body, options) {
      return request('POST', path, body === undefined ? null : body, options);
    };
    this.put = function (path, body, options) {
      return request('PUT', path, body === undefined ? null : body, options);
    };
    this.patch = function (path, body, options) {
      return request('PATCH', path, body === undefined ? null : body, options);
    };
    this.delete = function (path, options) {
      return request('DELETE', path, null, options);
    };
  }

  return ApiClientService;
});
