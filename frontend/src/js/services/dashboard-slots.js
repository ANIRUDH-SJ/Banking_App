define([], function () {
  'use strict';

  var providers = [];

  function registerSummary(provider) {
    providers.push(provider);
  }

  function summaries() {
    return Promise.all(providers.map(function (provider) {
      return Promise.resolve().then(function () {
        return provider.load();
      }).then(function (value) {
        return {
          id: provider.id,
          title: provider.title,
          status: 'ready',
          value: value
        };
      }, function () {
        return {
          id: provider.id,
          title: provider.title,
          status: 'error',
          value: null
        };
      });
    }));
  }

  function registered() {
    return providers.map(function (provider) {
      return { id: provider.id, title: provider.title };
    });
  }

  return {
    registerSummary: registerSummary,
    summaries: summaries,
    registered: registered
  };
});
