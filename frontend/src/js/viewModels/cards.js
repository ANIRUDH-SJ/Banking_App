define(
  ['knockout', 'ojs/ojarraydataprovider', '../services/CardService', '../accUtils'],
  function (ko, ArrayDataProvider, CardService, accUtils) {
    'use strict';

    function CardsViewModel() {
      this.cards = ko.observableArray([]);
      this.cardsProvider = new ArrayDataProvider(this.cards, {
        keyAttributes: 'cardId'
      });

      this.isLoading = ko.observable(false);
      this.errorMessage = ko.observable('');
      this.actionError = ko.observable('');
      this.pendingCardId = ko.observable(null);

      this.statusClass = (status) => `status-${typeof status === 'string' ? status.toLowerCase() : 'unknown'}`;
      this.hasStatus = (status, expected) => typeof status === 'string' && status.toUpperCase() === expected;

      this.formatExpiry = (month, year) => {
        if (!month || !year) {
          return '—';
        }

        return `${String(month).padStart(2, '0')}/${year}`;
      };

      this.refresh = async () => {
        this.isLoading(true);
        this.errorMessage('');
        this.actionError('');

        try {
          const result = await CardService.listCards();
          this.cards(Array.isArray(result) ? result : []);
        } catch (error) {
          this.errorMessage(error.message || 'Unable to load your cards.');
        } finally {
          this.isLoading(false);
        }
      };

      this.changeStatus = async (card, action) => {
        this.pendingCardId(card.cardId);
        this.actionError('');

        try {
          const updatedCard = await CardService.changeStatus(card.cardId, action);

          this.cards(
            this.cards().map((item) =>
              item.cardId === updatedCard.cardId ? updatedCard : item
            )
          );

          accUtils.announce(
            `Card status updated to ${updatedCard.status}.`,
            'polite'
          );
        } catch (error) {
          this.actionError(error.message || 'Unable to update card status.');
        } finally {
          this.pendingCardId(null);
        }
      };

      this.connected = () => {
        this.authenticatedHandler = this.authenticatedHandler || (() => this.refresh());
        window.addEventListener('netbanking:authenticated', this.authenticatedHandler);
        document.title = 'My cards';
        accUtils.announce('Cards page loaded.', 'polite');
        this.refresh();
      };

      this.disconnected = () => {
        if (this.authenticatedHandler) window.removeEventListener('netbanking:authenticated', this.authenticatedHandler);
      };
    }

    return CardsViewModel;
  }
);
