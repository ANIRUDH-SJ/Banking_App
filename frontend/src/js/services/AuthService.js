define([], function () {
  'use strict';

  const baseUrl = (window.__API_BASE_URL__ || 'http://localhost:8080').replace(/\/$/, '');
  let accessToken = null;
  let identity = null;

  // Remove tokens from older builds that stored them in browser storage.
  try {
    window.sessionStorage.removeItem('accessToken');
    window.localStorage.removeItem('accessToken');
  } catch (_) { /* Storage may be unavailable in a restricted browser context. */ }

  async function post(path, body) {
    const response = await fetch(`${baseUrl}${path}`, {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    const text = await response.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (_) { data = null; }
    if (!response.ok) {
      const error = new Error((data && (data.message || data.detail)) || `Sign-in failed (HTTP ${response.status}).`);
      error.status = response.status;
      throw error;
    }
    return data;
  }

  return {
    beginLogin(credentials) { return post('/api/v1/auth/login', credentials); },
    setupTotp(credentials) { return post('/api/v1/auth/totp/setup', credentials); },
    confirmTotp(credentials, code) {
      return post('/api/v1/auth/totp/confirm', { credentials, code });
    },
    verifyTotp(challengeId, code) {
      return post('/api/v1/auth/login/verify-totp', { challengeId, code });
    },
    setSession(response) {
      if (!response || !response.accessToken) throw new Error('The sign-in response did not include an access token.');
      accessToken = response.accessToken;
      identity = { username: response.username, userId: response.userId, roles: response.roles || [] };
      return identity;
    },
    getAccessToken() { return accessToken; },
    getIdentity() { return identity; },
    clearSession() { accessToken = null; identity = null; }
  };
});
