define(['./registry'], function (registry) {
  'use strict';

  function paymentBody(payment) {
    return {
      sourceAccountId: Number(payment.sourceAccountId),
      billerId: Number(payment.billerId),
      billReference: payment.billReference,
      amount: Number(payment.amount)
    };
  }

  return {
    listAccounts: function () {
      return registry.accounts.getAccounts();
    },
    listHistory: function () {
      return registry.apiClient.get('/api/v1/bill-payments');
    },
    requestOtp: function (payment) {
      return registry.otp.requestBillPaymentChallenge(paymentBody(payment));
    },
    submitPayment: function (payment) {
      return registry.apiClient.post('/api/v1/bill-payments', Object.assign(
        paymentBody(payment),
        {
          idempotencyKey: payment.idempotencyKey,
          otpChallengeId: payment.otpChallengeId,
          otpCode: payment.otpCode
        }
      ));
    }
  };
});
