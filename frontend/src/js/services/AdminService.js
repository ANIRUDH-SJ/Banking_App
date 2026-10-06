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
    listAuditEvents: function (params) { return list('/api/v1/admin/audit-events', params); }
  };
});
