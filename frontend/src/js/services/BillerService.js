define(['./AuthService'], function (AuthService) {
  'use strict';

  async function listActive() {
    const baseUrl = (window.__API_BASE_URL__ || 'http://localhost:8080')
      .replace(/\/$/, '');

    const token = AuthService.getAccessToken();
    if (!token) {
      window.dispatchEvent(new CustomEvent('netbanking:unauthorized'));
      throw new Error('Please sign in to view billers.');
    }

    const headers = { Accept: 'application/json', Authorization: `Bearer ${token}` };

    const response = await fetch(`${baseUrl}/api/v1/billers`, { headers });

    if (!response.ok) {
      if (response.status === 401) {
        AuthService.clearSession();
        window.dispatchEvent(new CustomEvent('netbanking:unauthorized'));
      }
      throw new Error(
        response.status === 401 || response.status === 403
          ? 'Please sign in to view billers.'
          : `Unable to load billers (HTTP ${response.status}).`
      );
    }

    const billers = await response.json();

    if (!Array.isArray(billers)) {
      throw new Error('The biller service returned an unexpected response.');
    }

    return billers;
  }

  return { listActive };
});
