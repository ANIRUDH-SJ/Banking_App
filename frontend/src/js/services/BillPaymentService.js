define(['./AuthService'], function (AuthService) {
  'use strict';

  const baseUrl = (window.__API_BASE_URL__ || 'http://localhost:8080')
    .replace(/\/$/, '');

  async function request(path, options = {}) {
    const token = AuthService.getAccessToken();

    if (!token) {
      window.dispatchEvent(new CustomEvent('netbanking:unauthorized'));
      throw new Error('Please sign in before making a bill payment.');
    }

    const response = await fetch(`${baseUrl}${path}`, {
      ...options,
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
        ...(options.headers || {})
      }
    });

    const responseText = await response.text();
    let responseData = null;

    try {
      responseData = responseText ? JSON.parse(responseText) : null;
    } catch (_) {
      responseData = null;
    }

    if (!response.ok) {
      if (response.status === 401) {
        AuthService.clearSession();
        window.dispatchEvent(new CustomEvent('netbanking:unauthorized'));
      }
      const error = new Error(
        (responseData && (responseData.message || responseData.detail)) ||
          `Request failed (HTTP ${response.status}).`
      );
      error.status = response.status;
      throw error;
    }

    return responseData;
  }

  return {
    listAccounts() {
      return request('/api/v1/accounts');
    },

    listHistory() {
      return request('/api/v1/bill-payments');
    },

    requestOtp(payment) {
      return request('/api/v1/bill-payments/otp-challenges', {
        method: 'POST',
        body: JSON.stringify({
          sourceAccountId: Number(payment.sourceAccountId),
          billerId: Number(payment.billerId),
          billReference: payment.billReference,
          amount: Number(payment.amount)
        })
      });
    },

    submitPayment(payment) {
      return request('/api/v1/bill-payments', {
        method: 'POST',
        body: JSON.stringify({
          sourceAccountId: Number(payment.sourceAccountId),
          billerId: Number(payment.billerId),
          billReference: payment.billReference,
          amount: Number(payment.amount),
          idempotencyKey: payment.idempotencyKey,
          otpChallengeId: payment.otpChallengeId,
          otpCode: payment.otpCode
        })
      });
    }
  };
});
