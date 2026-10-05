define([], function () {
  /**
   * Card reads for the home summary. The payments workspace owns card
   * controls and should extend this service rather than replace list().
   */
  function CardService(apiClient) {
    this.list = function () {
      return apiClient.get('/api/v1/cards');
    };

    this.getById = function (cardId) {
      return apiClient.get('/api/v1/cards/' + encodeURIComponent(cardId));
    };
  }

  return CardService;
});
