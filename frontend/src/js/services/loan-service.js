define([], function () {
  /**
   * Loan reads for the home summary. The payments workspace owns repayment
   * screens and should extend this service rather than replace list().
   * Loan repayment has no one-time-code challenge endpoint.
   */
  function LoanService(apiClient) {
    this.list = function () {
      return apiClient.get('/api/v1/loans');
    };

    this.getById = function (loanId) {
      return apiClient.get('/api/v1/loans/' + encodeURIComponent(loanId));
    };
  }

  return LoanService;
});
