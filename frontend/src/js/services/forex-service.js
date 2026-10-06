(function (factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  }
})(function () {
  function ForexService(apiClient) {
    this.list = function () { return apiClient.get('/api/v1/forex/conversions'); };
    this.quote = function (request) { return apiClient.post('/api/v1/forex/quotes', request); };
    this.convert = function (request) { return apiClient.post('/api/v1/forex/conversions', request); };
  }
  return ForexService;
});
