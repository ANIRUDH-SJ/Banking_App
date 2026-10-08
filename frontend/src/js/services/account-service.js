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

    this.downloadStatementPdf = function (accountId, query) {
      return apiClient.download('/api/v1/accounts/' + encodeURIComponent(accountId) + '/statement.pdf' + (query || ''));
    };

    /** Emails the PDF for the same filters to the customer's registered address. */
    this.emailStatement = function (accountId, filters) {
      return apiClient.post('/api/v1/accounts/' + encodeURIComponent(accountId) + '/statement-emails', filters || {});
    };

    /** The nickname is personal to the signed-in holder; an empty value clears it. */
    this.rename = function (accountId, nickname) {
      return apiClient.patch('/api/v1/accounts/' + encodeURIComponent(accountId), { nickname: nickname || null });
    };
  }

  return AccountService;
});
