define(['./registry'], function (registry) {
  'use strict';

  return {
    listCards: function () {
      return registry.cards.list();
    },
    changeStatus: function (cardId, action) {
      return registry.apiClient.patch(
        '/api/v1/cards/' + encodeURIComponent(cardId) + '/status',
        { action: action }
      );
    }
  };
});
