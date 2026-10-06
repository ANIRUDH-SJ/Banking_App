(function (factory) {
  if (typeof define === 'function' && define.amd) {
    define(['./validation-service'], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory(require('./validation-service'));
  }
})(function (ValidationService) {
  function encodeId(value) {
    return encodeURIComponent(String(value));
  }

  /**
   * Requests one-time codes for payment workspaces.
   * Each payment screen still submits its own confirmation, receipt, and status.
   * Loan repayment has no separate challenge endpoint; do not call this service for it.
   */
  function OtpService(apiClient, validation) {
    this.validation = validation || new ValidationService();

    this.requestTransferChallenge = function (sourceAccountId, beneficiaryId, amount) {
      return apiClient.post('/api/v1/transfers/otp-challenges', {
        sourceAccountId: sourceAccountId,
        beneficiaryId: beneficiaryId,
        amount: amount
      });
    };

    this.requestBeneficiaryActivation = function (beneficiaryId) {
      return apiClient.post('/api/v1/beneficiaries/' + encodeId(beneficiaryId) + '/activation-challenges');
    };

    this.requestBillPaymentChallenge = function (request) {
      return apiClient.post('/api/v1/bill-payments/otp-challenges', request);
    };

    this.requestForexChallenge = function (quoteId) {
      return apiClient.post('/api/v1/forex/quotes/' + encodeId(quoteId) + '/otp-challenges');
    };

    this.validateCode = function (code) {
      return this.validation.otp(code);
    };
  }

  return OtpService;
});
