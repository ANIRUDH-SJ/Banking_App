define(['./api-error'], function (ApiError) {
  'use strict';

  function message(error, fallback) {
    if (ApiError.isApiError(error) && error.message) {
      return error.message;
    }
    return fallback;
  }

  function field(error, name) {
    if (ApiError.isApiError(error) && error.fieldErrors && error.fieldErrors[name]) {
      return error.fieldErrors[name];
    }
    return '';
  }

  return {
    message: message,
    field: field
  };
});
