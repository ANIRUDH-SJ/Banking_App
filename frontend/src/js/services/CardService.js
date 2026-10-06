define(['./AuthService'], function (AuthService) {
  'use strict';

  const baseUrl = (window.__API_BASE_URL__ || 'http://localhost:8080')
    .replace(/\/$/, '');

  async function request(path, options = {}) {
    const token = AuthService.getAccessToken();

    if (!token) {
      window.dispatchEvent(new CustomEvent('netbanking:unauthorized'));
      throw new Error('Please sign in to view your cards.');
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

    try {
      data = text ? JSON.parse(text) : null;
    } catch (_) {
      data = null;
    }

    if (!response.ok) {
      if (response.status === 401) {
        AuthService.clearSession();
        window.dispatchEvent(new CustomEvent('netbanking:unauthorized'));
      }
      throw new Error(
        (data && (data.message || data.detail)) ||
          `Card request failed (HTTP ${response.status}).`
      );
    }

    return data;
  }

  return {
    listCards() {
      return request('/api/v1/cards');
    },

    changeStatus(cardId, action) {
      return request(`/api/v1/cards/${encodeURIComponent(cardId)}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ action })
      });
    }
  };
});
