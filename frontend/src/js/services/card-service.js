define([], function () {
  /**
   * Card reads and controls. PINs are passed in already sealed by
   * pin-crypto; this service never sees a plain PIN.
   */
  function CardService(apiClient) {
    function card(cardId, suffix) {
      return '/api/v1/cards/' + encodeURIComponent(cardId) + (suffix || '');
    }

    this.list = function () {
      return apiClient.get('/api/v1/cards');
    };

    this.getById = function (cardId) {
      return apiClient.get(card(cardId));
    };

    this.pinKey = function () {
      return apiClient.get('/api/v1/cards/pin-key');
    };

    /** The full number, for the few seconds the customer asked to see it. Never includes the CVV. */
    this.reveal = function (cardId) {
      return apiClient.post(card(cardId, '/reveal'));
    };

    this.setPin = function (cardId, sealed) {
      return apiClient.put(card(cardId, '/pin'), sealed);
    };

    this.transactions = function (cardId, page, size) {
      return apiClient.get(card(cardId, '/transactions?page=' + (page || 0) + '&size=' + (size || 20)));
    };

    this.emiOptions = function (cardId, transactionId) {
      return apiClient.get(card(cardId, '/transactions/' + encodeURIComponent(transactionId) + '/emi-options'));
    };

    this.convertToEmi = function (cardId, transactionId, tenureMonths, idempotencyKey) {
      return apiClient.post(card(cardId, '/transactions/' + encodeURIComponent(transactionId) + '/emi'), {
        tenureMonths: tenureMonths,
        idempotencyKey: idempotencyKey
      });
    };

    this.emiPlans = function (cardId) {
      return apiClient.get(card(cardId, '/emi-plans'));
    };
  }

  return CardService;
});
