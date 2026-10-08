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
    this.rates = function () { return apiClient.get('/api/v1/forex/rates'); };
    /** An indicative conversion; it does not hold the rate or move money. */
    this.preview = function (from, to, amount) {
      return apiClient.get('/api/v1/forex/rates/convert?from=' + encodeURIComponent(from) +
        '&to=' + encodeURIComponent(to) + '&amount=' + encodeURIComponent(amount));
    };
  }
  return ForexService;
});
