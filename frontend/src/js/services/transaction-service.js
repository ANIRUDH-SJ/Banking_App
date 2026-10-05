define([], function () {
  function TransactionService(apiClient) {
    this.list = function (accountId, query) {
      return apiClient.get(
        '/api/v1/accounts/' + accountId + '/transactions' + (query || '')
      );
    };

    this.getById = function (accountId, entryId) {
      return apiClient.get(
        '/api/v1/accounts/' + accountId + '/transactions/' + entryId
      );
    };

    this.getStatusHistory = function (accountId, entryId) {
      return apiClient.get(
        '/api/v1/accounts/' + accountId + '/transactions/' + entryId + '/status-history'
      );
    };
  }

  return TransactionService;
});
