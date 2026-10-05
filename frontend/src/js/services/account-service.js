define([], function () {
  /**
   * Member 2 account API contract.  The shared apiClient is supplied by the
   * application shell so authentication and error handling stay centralised.
   */
  function AccountService(apiClient) {
    this.getAccounts = function () {
      return apiClient.get('/api/v1/accounts');
    };

    this.getAccountById = function (accountId) {
      return apiClient.get('/api/v1/accounts/' + accountId);
    };

    this.getTransactions = function (accountId) {
      return apiClient.get(
        '/api/v1/accounts/' + accountId + '/transactions'
      );
    };

    this.downloadStatement = function (accountId) {
      return apiClient.get(
        '/api/v1/accounts/' + accountId + '/statement.csv'
      );
    };

    this.getStatement = function (accountId, query) {
      return apiClient.get(
        '/api/v1/accounts/' + accountId + '/statement' + (query || '')
      );
    };
  }

  return AccountService;
});
