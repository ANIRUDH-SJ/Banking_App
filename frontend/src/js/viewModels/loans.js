define(['knockout', 'ojs/ojarraydataprovider', '../services/LoanService', '../accUtils'],
  function (ko, ArrayDataProvider, LoanService, accUtils) {
    'use strict';

    function LoansViewModel() {
      this.loans = ko.observableArray([]);
      this.accounts = ko.observableArray([]);
      this.payments = ko.observableArray([]);
      this.loansProvider = new ArrayDataProvider(this.loans, { keyAttributes: 'loanId' });
      this.paymentsProvider = new ArrayDataProvider(this.payments, { keyAttributes: 'loanPaymentId' });
      this.screen = ko.observable('list');
      this.selectedLoan = ko.observable(null);
      this.sourceAccountId = ko.observable(null);
      this.amount = ko.observable('');
      this.isLoading = ko.observable(false);
      this.errorMessage = ko.observable('');
      this.paymentError = ko.observable('');
      this.receipt = ko.observable(null);
      this.idempotencyKey = ko.observable('');
      this.paymentPage = ko.observable(0);
      this.paymentPageCount = ko.observable(0);
      this.displayPaymentPageCount = ko.computed(() => Math.max(this.paymentPageCount(), 1));

      this.money = (amount, currency) => new Intl.NumberFormat('en-IN', {
        style: 'currency', currency: currency || 'INR'
      }).format(Number(amount || 0));
      this.date = (value) => value ? new Date(value).toLocaleDateString() : '—';
      this.isActiveLoan = (status) => typeof status === 'string' && status.toUpperCase() === 'ACTIVE';

      this.refresh = async () => {
        this.isLoading(true); this.errorMessage('');
        try { this.loans(await LoanService.listLoans()); }
        catch (error) { this.errorMessage(error.message || 'Unable to load loans.'); }
        finally { this.isLoading(false); }
      };

      this.openRepayment = async (loan) => {
        this.selectedLoan(loan); this.paymentError(''); this.amount(''); this.receipt(null);
        this.idempotencyKey(''); this.screen('repay'); this.isLoading(true);
        try {
          const accounts = await LoanService.listAccounts();
          this.accounts(accounts.filter((a) => a.accountStatus === 'ACTIVE' && a.currencyCode === loan.currencyCode));
          if (!this.accounts().length) this.paymentError('No active account in the loan currency is available.');
          else this.sourceAccountId(this.accounts()[0].accountId);
        } catch (error) { this.paymentError(error.message || 'Unable to load accounts.'); }
        finally { this.isLoading(false); }
      };

      this.showHistory = async (loan) => {
        this.selectedLoan(loan); this.screen('history'); this.isLoading(true); this.errorMessage('');
        try {
          await this.loadHistoryPage(0);
        } catch (error) { this.errorMessage(error.message || 'Unable to load repayment history.'); }
        finally { this.isLoading(false); }
      };

      this.loadHistoryPage = async (pageNumber) => {
        const page = await LoanService.listPayments(this.selectedLoan().loanId, pageNumber, 20);
        this.payments(page.content || []);
        this.paymentPage(page.page || 0);
        this.paymentPageCount(page.totalPages || 0);
      };

      this.changeHistoryPage = async (pageNumber) => {
        this.isLoading(true); this.errorMessage('');
        try { await this.loadHistoryPage(pageNumber); }
        catch (error) { this.errorMessage(error.message || 'Unable to load repayment history.'); }
        finally { this.isLoading(false); }
      };

      this.submitRepayment = async () => {
        const loan = this.selectedLoan();
        const value = Number(this.amount());
        if (!this.sourceAccountId()) { this.paymentError('Select an account.'); return; }
        if (!Number.isFinite(value) || value <= 0 || value > Number(loan.outstandingPrincipal)) {
          this.paymentError('Enter an amount greater than zero and no more than the outstanding balance.'); return;
        }
        this.isLoading(true); this.paymentError('');
        try {
          if (!this.idempotencyKey()) this.idempotencyKey(crypto.randomUUID());
          const result = await LoanService.makePayment(loan.loanId, {
            sourceAccountId: this.sourceAccountId(), amount: value, idempotencyKey: this.idempotencyKey()
          });
          this.receipt(result); this.screen('receipt');
          this.idempotencyKey('');
          this.loans(this.loans().map((item) => item.loanId === loan.loanId
            ? Object.assign({}, item, { outstandingPrincipal: result.outstandingAfter })
            : item));
        } catch (error) { this.paymentError(error.message || 'Loan repayment failed.'); }
        finally { this.isLoading(false); }
      };

      this.backToLoans = () => { this.errorMessage(''); this.paymentError(''); this.screen('list'); };
      this.connected = () => {
        this.authenticatedHandler = this.authenticatedHandler || (() => this.refresh());
        window.addEventListener('netbanking:authenticated', this.authenticatedHandler);
        document.title = 'Loans'; accUtils.announce('Loans page loaded.', 'polite'); this.refresh();
      };
      this.disconnected = () => {
        if (this.authenticatedHandler) window.removeEventListener('netbanking:authenticated', this.authenticatedHandler);
      };
    }
    return LoansViewModel;
  });
