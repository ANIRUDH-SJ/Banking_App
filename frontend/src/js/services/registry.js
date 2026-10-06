define([
  './session-service',
  './api-client-service',
  './auth-service',
  './otp-service',
  './validation-service',
  './profile-service',
  './notification-service',
  './route-guard-service',
  './auth-flow-state',
  './account-service',
  './transaction-service',
  './card-service',
  './loan-service',
  './deposit-service',
  './forex-service'
], function (
  SessionService,
  ApiClientService,
  AuthService,
  OtpService,
  ValidationService,
  ProfileService,
  NotificationService,
  routeGuard,
  authFlow,
  AccountService,
  TransactionService,
  CardService,
  LoanService,
  DepositService,
  ForexService
) {
  var handlers = {};
  var router = null;
  var baseUrl = (typeof window !== 'undefined' && window.NET_BANKING_API_BASE) || '';
  var session = new SessionService(window.sessionStorage);
  var validation = new ValidationService();
  var apiClient = new ApiClientService({
    session: session,
    fetch: window.fetch.bind(window),
    baseUrl: baseUrl
  });

  return {
    session: session,
    apiClient: apiClient,
    auth: new AuthService(apiClient),
    otp: new OtpService(apiClient, validation),
    validation: validation,
    profile: new ProfileService(apiClient),
    notifications: new NotificationService(apiClient),
    accounts: new AccountService(apiClient),
    transactions: new TransactionService(apiClient),
    cards: new CardService(apiClient),
    loans: new LoanService(apiClient),
    deposits: new DepositService(apiClient),
    forex: new ForexService(apiClient),
    routeGuard: routeGuard,
    authFlow: authFlow,
    events: {
      on: function (name, handler) {
        handlers[name] = handlers[name] || [];
        handlers[name].push(handler);
        return function () {
          handlers[name] = (handlers[name] || []).filter(function (item) { return item !== handler; });
        };
      },
      emit: function (name, payload) {
        (handlers[name] || []).slice().forEach(function (handler) { handler(payload); });
      }
    },
    setRouter: function (coreRouter) {
      router = coreRouter;
    },
    go: function (path) {
      if (router) {
        return router.go({ path: path });
      }
      return Promise.resolve();
    }
  };
});
