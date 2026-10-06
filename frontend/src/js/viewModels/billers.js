define(
  [
    'knockout',
    'ojs/ojarraydataprovider',
    '../services/BillerService',
    '../services/BillPaymentService',
    '../accUtils'
  ],
  function (ko, ArrayDataProvider, BillerService, BillPaymentService, accUtils) {
    'use strict';

    function BillersViewModel() {
      // Catalogue data
      this.allBillers = ko.observableArray([]);
      this.filteredBillers = ko.observableArray([]);
      this.categories = ko.observableArray([
        { value: 'all', label: 'All categories' }
      ]);

      // Payment history data
      this.history = ko.observableArray([]);
      this.historyLoading = ko.observable(false);
      this.historyError = ko.observable('');

      // Account data
      this.accounts = ko.observableArray([]);

      // General page state
      this.isLoading = ko.observable(false);
      this.errorMessage = ko.observable('');
      this.paymentError = ko.observable('');
      this.searchText = ko.observable('');
      this.selectedCategory = ko.observable('all');
      this.screen = ko.observable('catalogue');

      // Current payment state
      this.selectedBiller = ko.observable(null);
      this.sourceAccountId = ko.observable(null);
      this.billReference = ko.observable('');
      this.amount = ko.observable('');
      this.otpCode = ko.observable('');
      this.otpChallengeId = ko.observable('');
      this.idempotencyKey = ko.observable('');
      this.receipt = ko.observable(null);

      // ArrayDataProvider accepts arrays or Knockout observableArrays.
      this.categoryProvider = new ArrayDataProvider(this.categories, {
        keyAttributes: 'value'
      });

      this.billersProvider = new ArrayDataProvider(this.filteredBillers, {
        keyAttributes: 'billerId'
      });

      this.historyProvider = new ArrayDataProvider(this.history, {
        keyAttributes: 'paymentId'
      });

      this.resultCountText = ko.pureComputed(() => {
        const count = this.filteredBillers().length;
        return `${count} ${count === 1 ? 'biller' : 'billers'}`;
      });

      this.formatAmount = (value) =>
        new Intl.NumberFormat('en-IN', {
          style: 'currency',
          currency: 'INR',
          maximumFractionDigits: 2
        }).format(Number(value));

      this.formatReceiptAmount = (value, currencyCode) =>
        new Intl.NumberFormat('en-IN', {
          style: 'currency',
          currency: currencyCode || 'INR'
        }).format(Number(value));

      this.getInitials = (name) =>
        String(name || '?')
          .split(/\s+/)
          .slice(0, 2)
          .map((part) => part.charAt(0))
          .join('')
          .toUpperCase();

      this.updateCategories = () => {
        const names = [
          ...new Set(
            this.allBillers()
              .map((biller) => biller.category)
              .filter(Boolean)
          )
        ].sort();

        this.categories([
          { value: 'all', label: 'All categories' },
          ...names.map((name) => ({ value: name, label: name }))
        ]);

        if (
          this.selectedCategory() !== 'all' &&
          !names.includes(this.selectedCategory())
        ) {
          this.selectedCategory('all');
        }
      };

      this.updateFilteredBillers = () => {
        const query = this.searchText().trim().toLowerCase();
        const category = this.selectedCategory();

        const results = this.allBillers().filter((biller) => {
          const categoryMatches =
            category === 'all' || biller.category === category;

          const searchMatches =
            !query ||
            [biller.name, biller.category, biller.code].some((value) =>
              String(value || '').toLowerCase().includes(query)
            );

          return categoryMatches && searchMatches;
        });

        this.filteredBillers(results);
      };

      this.allBillers.subscribe(() => {
        this.updateCategories();
        this.updateFilteredBillers();
      });

      this.searchText.subscribe(this.updateFilteredBillers);
      this.selectedCategory.subscribe(this.updateFilteredBillers);

      this.refresh = async () => {
        this.isLoading(true);
        this.errorMessage('');

        try {
          const billers = await BillerService.listActive();
          this.allBillers(billers);
        } catch (error) {
          this.errorMessage(error.message || 'Unable to load billers.');
        } finally {
          this.isLoading(false);
        }
      };

      this.startPayment = async (biller) => {
        this.selectedBiller(biller);
        this.paymentError('');
        this.billReference('');
        this.amount('');
        this.otpCode('');
        this.otpChallengeId('');
        this.idempotencyKey('');
        this.receipt(null);
        this.accounts([]);
        this.screen('form');
        this.isLoading(true);

        try {
          const accountList = await BillPaymentService.listAccounts();

          const eligibleAccounts = accountList.filter(
            (account) => account.accountStatus === 'ACTIVE'
          );

          this.accounts(
            eligibleAccounts.map((account) => ({
              accountId: account.accountId,
              label: `${account.accountType} • ${account.accountNumber} — ${account.currencyCode} ${account.availableBalance}`
            }))
          );

          if (this.accounts().length === 0) {
            this.paymentError('No active account is available for payment.');
          } else {
            this.sourceAccountId(this.accounts()[0].accountId);
          }
        } catch (error) {
          this.paymentError(error.message || 'Unable to load your accounts.');
        } finally {
          this.isLoading(false);
        }
      };

      this.reviewPayment = () => {
        const biller = this.selectedBiller();
        const amountValue = Number(this.amount());

        if (!biller) {
          this.paymentError('Select a biller first.');
          return;
        }

        if (!this.sourceAccountId()) {
          this.paymentError('Select an account to pay from.');
          return;
        }

        if (!this.billReference().trim()) {
          this.paymentError('Enter the bill reference.');
          return;
        }

        if (
          !Number.isFinite(amountValue) ||
          amountValue < Number(biller.minAmount) ||
          amountValue > Number(biller.maxAmount)
        ) {
          this.paymentError('Enter an amount within this biller’s allowed range.');
          return;
        }

        this.paymentError('');
        this.screen('review');
      };

      this.requestPaymentOtp = async () => {
        const biller = this.selectedBiller();

        if (!biller) {
          this.paymentError('Select a biller first.');
          return;
        }

        this.isLoading(true);
        this.paymentError('');

        try {
          const response = await BillPaymentService.requestOtp({
            sourceAccountId: this.sourceAccountId(),
            billerId: biller.billerId,
            billReference: this.billReference().trim(),
            amount: this.amount()
          });

          this.otpChallengeId(response.challengeId);
          this.otpCode('');

          // Keep this key for retries of this exact payment attempt.
          this.idempotencyKey(crypto.randomUUID());

          this.screen('otp');
        } catch (error) {
          this.paymentError(error.message || 'Could not request an OTP.');
        } finally {
          this.isLoading(false);
        }
      };

      this.submitPayment = async () => {
        const biller = this.selectedBiller();

        if (!biller) {
          this.paymentError('Select a biller first.');
          return;
        }

        if (!/^[0-9]{6}$/.test(this.otpCode())) {
          this.paymentError('Enter the 6-digit OTP.');
          return;
        }

        this.isLoading(true);
        this.paymentError('');

        try {
          const response = await BillPaymentService.submitPayment({
            sourceAccountId: this.sourceAccountId(),
            billerId: biller.billerId,
            billReference: this.billReference().trim(),
            amount: this.amount(),
            idempotencyKey: this.idempotencyKey(),
            otpChallengeId: this.otpChallengeId(),
            otpCode: this.otpCode()
          });

          this.receipt(response);
          this.screen('receipt');
        } catch (error) {
          this.paymentError(error.message || 'Payment could not be completed.');
        } finally {
          this.isLoading(false);
        }
      };

      this.showHistory = async () => {
        this.screen('history');
        this.history([]);
        this.historyLoading(true);
        this.historyError('');

        try {
          const payments = await BillPaymentService.listHistory();
          this.history(payments);
        } catch (error) {
          this.historyError(error.message || 'Unable to load payment history.');
        } finally {
          this.historyLoading(false);
        }
      };

      this.backToCatalogue = () => {
        this.paymentError('');
        this.historyError('');
        this.screen('catalogue');
      };

      this.backToForm = () => {
        this.paymentError('');
        this.screen('form');
      };

      this.backToReview = () => {
        this.paymentError('');
        this.screen('review');
      };

      this.connected = () => {
        this.authenticatedHandler = this.authenticatedHandler || (() => this.refresh());
        window.addEventListener('netbanking:authenticated', this.authenticatedHandler);
        accUtils.announce('Biller catalogue page loaded.', 'polite');
        document.title = 'Biller catalogue';
        this.refresh();
      };

      this.disconnected = () => {
        if (this.authenticatedHandler) window.removeEventListener('netbanking:authenticated', this.authenticatedHandler);
      };
    }

    return BillersViewModel;
  }
);
