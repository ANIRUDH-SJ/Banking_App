define([], function () {
  /**
   * Account reads used by the home desk. The accounts workspace owns the
   * account screens and may extend this service. Keep getAccounts() stable.
   */
  function AccountService(apiClient) {
    this.getAccounts = function () {
      return apiClient.get('/api/v1/accounts');
    };

    this.openForeignCurrency = function (currencyCode) {
      return apiClient.post('/api/v1/accounts/foreign-currency', { currencyCode: currencyCode });
    };

    this.getAccountById = function (accountId) {
      return apiClient.get('/api/v1/accounts/' + encodeURIComponent(accountId));
    };

    this.getTransactions = function (accountId) {
      return apiClient.get('/api/v1/accounts/' + encodeURIComponent(accountId) + '/transactions');
    };

    this.downloadStatement = function (accountId, query) {
      return apiClient.get('/api/v1/accounts/' + encodeURIComponent(accountId) + '/statement.csv' + (query || ''));
    };

    this.getStatement = function (accountId, query) {
      return apiClient.get('/api/v1/accounts/' + encodeURIComponent(accountId) + '/statement' + (query || ''));
    };
  }

  return AccountService;
});
