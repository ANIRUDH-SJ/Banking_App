define(['./api-error'], function (ApiError) {
  'use strict';

  function isAuthPath(path) {
    return path.indexOf('/api/v1/auth/') === 0;
  }

  function readBody(response) {
    return response.text().then(function (text) {
      if (!text) {
        return null;
      }
      try {
        return JSON.parse(text);
      } catch (error) {
        return { message: text };
      }
    });
  }

  function ApiClientService(options) {
    var settings = options || {};
    this.baseUrl = settings.baseUrl || '';
    this.session = settings.session;
    this.fetchImpl = settings.fetchImpl || fetch;
  }

  ApiClientService.prototype.request = function (method, path, body) {
    var self = this;
    var headers = { Accept: 'application/json' };
    if (body !== undefined && body !== null) {
      headers['Content-Type'] = 'application/json';
    }
    if (!isAuthPath(path) && this.session) {
      var authorization = this.session.authorizationHeader();
      if (authorization) {
        headers.Authorization = authorization;
      }
    }
    return this.fetchImpl(this.baseUrl + path, {
      method: method,
      headers: headers,
      body: body == null ? undefined : JSON.stringify(body),
      credentials: 'omit',
      cache: 'no-store'
    }).then(function (response) {
      return readBody(response).then(function (payload) {
        if (response.status === 204 || response.status === 205) {
          return null;
        }
        if (!response.ok) {
          var error = ApiError.fromPayload(response.status, payload);
          if (response.status === 401 && headers.Authorization && self.session) {
            self.session.discardUnauthorized();
          }
          throw error;
        }
        return payload;
      });
    }, function () {
      throw new ApiError({
        status: 0,
        code: 'NETWORK',
        message: 'The banking service could not be reached. Try again.'
      });
    });
  };

  ApiClientService.prototype.get = function (path) {
    return this.request('GET', path);
  };

  ApiClientService.prototype.post = function (path, body) {
    return this.request('POST', path, body);
  };

  ApiClientService.prototype.put = function (path, body) {
    return this.request('PUT', path, body);
  };

  ApiClientService.prototype.patch = function (path, body) {
    return this.request('PATCH', path, body == null ? {} : body);
  };

  ApiClientService.prototype.delete = function (path) {
    return this.request('DELETE', path);
  };

  return ApiClientService;
});
