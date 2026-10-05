define([], function () {
  function TransactionService(apiClient) {
    this.list = function (accountId, query) {
      return apiClient.get('/api/v1/accounts/' + encodeURIComponent(accountId) + '/transactions' + (query || ''));
    };

    this.getById = function (accountId, entryId) {
      return apiClient.get('/api/v1/accounts/' + encodeURIComponent(accountId) + '/transactions/' + encodeURIComponent(entryId));
    };

    this.getStatusHistory = function (accountId, entryId) {
      return apiClient.get(
        '/api/v1/accounts/' + encodeURIComponent(accountId) + '/transactions/' + encodeURIComponent(entryId) + '/status-history'
      );
    };
  }

  return TransactionService;
});
