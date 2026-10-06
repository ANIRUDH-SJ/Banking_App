define(['./AuthService'], function (AuthService) {
  'use strict';

  const baseUrl = (window.__API_BASE_URL__ || 'http://localhost:8080').replace(/\/$/, '');

  async function get(path, params = {}) {
    const token = AuthService.getAccessToken();
    if (!token) {
      window.dispatchEvent(new CustomEvent('netbanking:unauthorized'));
      throw new Error('Please sign in with an administrator account.');
    }
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && String(value).trim() !== '') query.set(key, value);
    });
    const queryString = query.toString();
    const url = `${baseUrl}${path}${queryString ? `?${queryString}` : ''}`;
    const response = await fetch(url, {
      headers: { Accept: 'application/json', Authorization: `Bearer ${token}` }
    });
    const text = await response.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (_) { data = null; }
    if (!response.ok) {
      if (response.status === 401) {
        AuthService.clearSession();
        window.dispatchEvent(new CustomEvent('netbanking:unauthorized'));
      }
      const error = new Error((data && (data.message || data.detail)) || `Admin request failed (HTTP ${response.status}).`);
      error.status = response.status;
      throw error;
    }
    return data;
  }

  return {
    listUsers(params = {}) { return get('/api/v1/admin/users', params); },
    listAccounts(params = {}) { return get('/api/v1/admin/accounts', params); },
    listTransactions(params = {}) { return get('/api/v1/admin/transactions', params); },
    listAuditEvents(params = {}) { return get('/api/v1/admin/audit-events', params); }
  };
});
