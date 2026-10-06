(function (factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  }
})(function () {
  function DepositService(apiClient) {
    this.list = function () { return apiClient.get('/api/v1/deposits'); };
    this.get = function (depositId) { return apiClient.get('/api/v1/deposits/' + encodeURIComponent(depositId)); };
    this.quote = function (request) { return apiClient.post('/api/v1/deposits/quotes', request); };
    this.open = function (quoteId, idempotencyKey) {
      return apiClient.post('/api/v1/deposits', { quoteId: quoteId, idempotencyKey: idempotencyKey });
    };
    this.collectInstallment = function (depositId) {
      return apiClient.post('/api/v1/deposits/' + encodeURIComponent(depositId) + '/installments');
    };
  }
  return DepositService;
});
