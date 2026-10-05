define([
  './app-config',
  './services/session-service',
  './services/api-client-service',
  './services/auth-flow-state',
  './services/auth-service',
  './services/validation-service',
  './services/otp-service',
  './services/route-guard-service',
  './services/profile-service',
  './services/notification-service',
  './services/dashboard-slots',
  './services/session-notices',
  './services/error-message'
], function (
  config,
  SessionService,
  ApiClientService,
  flowState,
  AuthService,
  validation,
  OtpService,
  routeGuard,
  ProfileService,
  NotificationService,
  dashboardSlots,
  notices,
  errorMessage
) {
  'use strict';

  var storage = typeof window !== 'undefined' ? window.sessionStorage : null;
  var session = new SessionService({ storage: storage });
  var apiClient = new ApiClientService({
    baseUrl: config.apiBaseUrl,
    session: session
  });
  var guard = function () {
    return true;
  };
  var router = null;

  return {
    config: config,
    session: session,
    apiClient: apiClient,
    flowState: flowState,
    auth: new AuthService(apiClient, flowState),
    validation: validation,
    otp: new OtpService(apiClient, validation),
    routeGuard: routeGuard,
    profile: new ProfileService(apiClient),
    notifications: new NotificationService(apiClient),
    dashboardSlots: dashboardSlots,
    notices: notices,
    errorMessage: errorMessage,
    setGuard: function (nextGuard) {
      guard = nextGuard;
    },
    ensure: function (path) {
      return guard(path);
    },
    setRouter: function (nextRouter) {
      router = nextRouter;
    },
    go: function (path) {
      if (router) {
        return router.go({ path: path });
      }
      return undefined;
    }
  };
});
