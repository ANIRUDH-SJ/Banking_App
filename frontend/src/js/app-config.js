define([], function () {
  'use strict';

  var configured = typeof window !== 'undefined' && window.NET_BANKING_API_BASE;
  return {
    apiBaseUrl: configured || 'http://localhost:8080'
  };
});
