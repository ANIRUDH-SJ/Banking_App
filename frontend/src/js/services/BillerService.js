define(['./registry'], function (registry) {
  'use strict';

  return {
    listActive: function () {
      return registry.apiClient.get('/api/v1/billers').then(function (billers) {
        if (!Array.isArray(billers)) {
          throw new Error('The biller service returned an unexpected response.');
        }
        return billers;
      });
    }
  };
});
