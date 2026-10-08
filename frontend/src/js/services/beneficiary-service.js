define([], function () {
  function BeneficiaryService(apiClient) {
    this.list = function () {
      return apiClient.get('/api/v1/beneficiaries');
    };

    this.create = function (beneficiary) {
      return apiClient.post('/api/v1/beneficiaries', beneficiary);
    };

    this.createActivationChallenge = function (beneficiaryId) {
      return apiClient.post(
        '/api/v1/beneficiaries/' + beneficiaryId + '/activation-challenges'
      );
    };

    this.activate = function (beneficiaryId, otpChallengeId, otpCode) {
      return apiClient.post('/api/v1/beneficiaries/' + beneficiaryId + '/activate', {
        otpChallengeId: otpChallengeId,
        otpCode: otpCode
      });
    };

    this.rename = function (beneficiaryId, nickname) {
      return apiClient.patch('/api/v1/beneficiaries/' + beneficiaryId, { nickname: nickname });
    };

    this.disable = function (beneficiaryId) {
      return apiClient.delete('/api/v1/beneficiaries/' + beneficiaryId);
    };
  }

  return BeneficiaryService;
});
