define(['./AuthService'], function (AuthService) {
  'use strict';

  const baseUrl = (window.__API_BASE_URL__ || 'http://localhost:8080').replace(/\/$/, '');

  async function request(path, options = {}) {
    const token = AuthService.getAccessToken();
    if (!token) {
      window.dispatchEvent(new CustomEvent('netbanking:unauthorized'));
      throw new Error('Please sign in to access your loans.');
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
    const text = await response.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (_) { data = null; }
    if (!response.ok) {
      if (response.status === 401) {
        AuthService.clearSession();
        window.dispatchEvent(new CustomEvent('netbanking:unauthorized'));
      }
      throw new Error((data && (data.message || data.detail)) || `Loan request failed (HTTP ${response.status}).`);
    }
    return data;
  }

  return {
    listLoans() { return request('/api/v1/loans'); },
    listAccounts() { return request('/api/v1/accounts'); },
    listPayments(loanId, page = 0, size = 20) {
      const query = new URLSearchParams({ page: String(page), size: String(size) });
      return request(`/api/v1/loans/${encodeURIComponent(loanId)}/payments?${query}`);
    },
    makePayment(loanId, payment) {
      return request(`/api/v1/loans/${encodeURIComponent(loanId)}/payments`, {
        method: 'POST',
        body: JSON.stringify({
          sourceAccountId: Number(payment.sourceAccountId),
          amount: Number(payment.amount),
          idempotencyKey: payment.idempotencyKey
        })
      });
    }
  };
});
