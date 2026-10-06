define([], function () {
  function FundTransferService(apiClient) {
    this.createOtpChallenge = function (sourceAccountId, beneficiaryId, amount) {
      return apiClient.post('/api/v1/transfers/otp-challenges', {
        sourceAccountId: sourceAccountId,
        beneficiaryId: beneficiaryId,
        amount: amount
      });
    };

    this.transfer = function (request) {
      return apiClient.post('/api/v1/transfers', request);
    };

    this.list = function () {
      return apiClient.get('/api/v1/transfers');
    };
  }

  return FundTransferService;
});
