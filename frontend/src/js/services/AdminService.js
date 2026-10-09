define(['./registry'], function (registry) {
  'use strict';

  function list(path, params) {
    var query = new URLSearchParams();
    Object.entries(params || {}).forEach(function (entry) {
      if (entry[1] !== undefined && entry[1] !== null && String(entry[1]).trim() !== '') {
        query.set(entry[0], entry[1]);
      }
    });
    var suffix = query.toString();
    return registry.apiClient.get(path + (suffix ? '?' + suffix : ''));
  }

  return {
    listUsers: function (params) { return list('/api/v1/admin/users', params); },
    listAccounts: function (params) { return list('/api/v1/admin/accounts', params); },
    listTransactions: function (params) { return list('/api/v1/admin/transactions', params); },
    listLoans: function (params) { return list('/api/v1/admin/loans', params); },
    loanSummary: function () { return registry.apiClient.get('/api/v1/admin/loans/summary'); },
    listAuditEvents: function (params) { return list('/api/v1/admin/audit-events', params); },
    updateUserStatus: function (userId, status) {
      return registry.apiClient.patch('/api/v1/admin/users/' + encodeURIComponent(userId) + '/status', { status: status });
    },
    updateAccountStatus: function (accountId, status) {
      return registry.apiClient.patch('/api/v1/admin/accounts/' + encodeURIComponent(accountId) + '/status', { status: status });
    }
  };
});
