define([], function () {
  'use strict';

  function ApiError(details) {
    this.name = 'ApiError';
    this.status = details.status;
    this.code = details.code || 'REQUEST_FAILED';
    this.message = details.message || 'The request could not be completed.';
    this.fieldErrors = details.fieldErrors || {};
    this.correlationId = details.correlationId || null;
  }

  ApiError.prototype = Object.create(Error.prototype);
  ApiError.prototype.constructor = ApiError;

  ApiError.fromPayload = function (status, payload) {
    var body = payload && typeof payload === 'object' ? payload : {};
    return new ApiError({
      status: status,
      code: body.code || 'REQUEST_FAILED',
      message: body.message || 'The request could not be completed.',
      fieldErrors: body.fieldErrors || {},
      correlationId: body.correlationId || null
    });
  };

  ApiError.isApiError = function (error) {
    return !!error && error.name === 'ApiError';
  };

  return ApiError;
});
