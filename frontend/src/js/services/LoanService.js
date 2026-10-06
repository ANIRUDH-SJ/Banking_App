define(['./registry'], function (registry) {
  'use strict';

  return {
    listLoans: function () {
      return registry.loans.list();
    },
    listAccounts: function () {
      return registry.accounts.getAccounts();
    },
    listPayments: function (loanId, page, size) {
      var query = new URLSearchParams({
        page: String(page == null ? 0 : page),
        size: String(size == null ? 20 : size)
      });
      return registry.apiClient.get(
        '/api/v1/loans/' + encodeURIComponent(loanId) + '/payments?' + query.toString()
      );
    },
    makePayment: function (loanId, payment) {
      return registry.apiClient.post(
        '/api/v1/loans/' + encodeURIComponent(loanId) + '/payments',
        {
          sourceAccountId: Number(payment.sourceAccountId),
          amount: Number(payment.amount),
          idempotencyKey: payment.idempotencyKey
        }
      );
    }
  };
});
