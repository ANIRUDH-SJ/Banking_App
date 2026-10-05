define([], function () {
  'use strict';

  function encodePath(value) {
    return encodeURIComponent(String(value));
  }

  function OtpService(apiClient, validation) {
    this.apiClient = apiClient;
    this.validation = validation;
  }

  OtpService.prototype.issue = function (path, body) {
    return this.apiClient.post(path, body == null ? {} : body);
  };

  OtpService.prototype.transferChallenge = function (request) {
    return this.issue('/api/v1/transfers/otp-challenges', request);
  };

  OtpService.prototype.billPaymentChallenge = function (request) {
    return this.issue('/api/v1/bill-payments/otp-challenges', request);
  };

  OtpService.prototype.beneficiaryActivationChallenge = function (beneficiaryId) {
    return this.issue('/api/v1/beneficiaries/' + encodePath(beneficiaryId) + '/activation-challenges');
  };

  OtpService.prototype.forexChallenge = function (quoteId) {
    return this.issue('/api/v1/forex/quotes/' + encodePath(quoteId) + '/otp-challenges');
  };

  OtpService.prototype.validateCode = function (code) {
    return this.validation.otpCode(code);
  };

  return OtpService;
});
