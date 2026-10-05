(function (factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  }
})(function () {
  function ApiError(details) {
    this.name = 'ApiError';
    this.status = details.status;
    this.code = details.code || 'REQUEST_FAILED';
    this.message = details.message || 'The request could not be completed.';
    this.path = details.path || '';
    this.correlationId = details.correlationId || '';
    this.fieldErrors = details.fieldErrors || {};
    this.retryAfter = details.retryAfter || '';
  }

  ApiError.prototype = Object.create(Error.prototype);
  ApiError.prototype.constructor = ApiError;

  function coerceBody(payload) {
    if (payload && typeof payload === 'object') {
      return payload;
    }
    if (typeof payload === 'string' && payload) {
      try {
        return JSON.parse(payload);
      } catch (ignore) {
        return { message: payload };
      }
    }
    return null;
  }

  function parseApiError(status, payload, path, retryAfter) {
    var body = coerceBody(payload) || {};
    var fieldErrors = body.fieldErrors && typeof body.fieldErrors === 'object' ? body.fieldErrors : {};
    return new ApiError({
      status: typeof body.status === 'number' ? body.status : status,
      code: body.code || (status === 401 ? 'UNAUTHORIZED' : 'REQUEST_FAILED'),
      message: body.message || 'The request could not be completed.',
      path: body.path || path || '',
      correlationId: body.correlationId || '',
      fieldErrors: fieldErrors,
      retryAfter: retryAfter || ''
    });
  }

  function fieldMessage(error, field) {
    if (!error || !error.fieldErrors) {
      return '';
    }
    return error.fieldErrors[field] || '';
  }

  return {
    ApiError: ApiError,
    parseApiError: parseApiError,
    fieldMessage: fieldMessage
  };
});
