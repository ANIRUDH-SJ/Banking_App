(function (factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  }
})(function () {
  var PUBLIC = ['login', 'register', 'password-recovery', 'totp-setup', 'totp-verify', 'session-expired'];
  var CUSTOMER = [
    'dashboard', 'profile', 'notifications', 'accounts', 'transactions',
    'beneficiaries', 'transfer', 'billers', 'bill-payments', 'cards', 'loans', 'deposits', 'forex'
  ];
  var ADMIN = ['admin', 'admin-users', 'admin-accounts', 'admin-transactions', 'admin-audit'];

  var CUSTOMER_NAV = [
    { path: 'dashboard', label: 'Home' },
    { path: 'accounts', label: 'Accounts' },
    { path: 'transactions', label: 'Transactions' },
    { path: 'beneficiaries', label: 'Beneficiaries' },
    { path: 'transfer', label: 'Transfer' },
    { path: 'billers', label: 'Billers' },
    { path: 'bill-payments', label: 'Bill payments' },
    { path: 'cards', label: 'Cards' },
    { path: 'loans', label: 'Loans' },
    { path: 'deposits', label: 'FD & RD' },
    { path: 'forex', label: 'Forex' },
    { path: 'profile', label: 'Profile' },
    { path: 'notifications', label: 'Notices' }
  ];

  var ADMIN_NAV = [
    { path: 'admin', label: 'Administration' },
    { path: 'admin-users', label: 'User monitoring' },
    { path: 'admin-accounts', label: 'Account monitoring' },
    { path: 'admin-transactions', label: 'Transaction monitoring' },
    { path: 'admin-audit', label: 'Audit' }
  ];

  var FEATURES = {
    accounts: {
      title: 'Accounts',
      owner: 'Accounts and transfers',
      detail: 'Account numbers, balances, and status are loaded from the accounts service. The accounts workspace supplies this screen.'
    },
    transactions: {
      title: 'Transactions',
      owner: 'Accounts and transfers',
      detail: 'Transaction history, filters, and statement export are supplied by the accounts workspace.'
    },
    beneficiaries: {
      title: 'Beneficiaries',
      owner: 'Accounts and transfers',
      detail: 'Beneficiary registration and activation are supplied by the accounts workspace. Activation uses the shared one-time code service.'
    },
    transfer: {
      title: 'Transfer',
      owner: 'Accounts and transfers',
      detail: 'Review, confirmation, receipt, and transfer status are supplied by the accounts workspace. The shared one-time code service requests the transfer code.'
    },
    billers: {
      title: 'Billers',
      owner: 'Payments and administration',
      detail: 'The biller catalogue is supplied by the payments workspace.'
    },
    'bill-payments': {
      title: 'Bill payments',
      owner: 'Payments and administration',
      detail: 'Bill payment review, submission, and history are supplied by the payments workspace. Confirmation uses the shared one-time code service.'
    },
    cards: {
      title: 'Cards',
      owner: 'Payments and administration',
      detail: 'Masked card details and permitted card controls are supplied by the payments workspace.'
    },
    loans: {
      title: 'Loans',
      owner: 'Payments and administration',
      detail: 'Loan details and repayments are supplied by the payments workspace.'
    },
    deposits: {
      title: 'Fixed & recurring deposits',
      owner: 'Savings and investments',
      detail: 'Open a fixed or recurring deposit after reviewing a time-limited rate quote. Deposit funding uses an eligible INR account.'
    },
    forex: {
      title: 'Foreign exchange',
      owner: 'Foreign exchange',
      detail: 'Review a time-limited exchange quote and confirm a conversion with a one-time code.'
    },
    admin: {
      title: 'Administration',
      owner: 'Payments and administration',
      detail: 'The administration desk is supplied by the payments workspace. Access to it is limited to administrator sign-in.'
    },
    'admin-users': {
      title: 'User monitoring',
      owner: 'Payments and administration',
      detail: 'User monitoring requests stay in the administration service.'
    },
    'admin-accounts': {
      title: 'Account monitoring',
      owner: 'Payments and administration',
      detail: 'Account monitoring requests stay in the administration service.'
    },
    'admin-transactions': {
      title: 'Transaction monitoring',
      owner: 'Payments and administration',
      detail: 'Transaction monitoring requests stay in the administration service.'
    },
    'admin-audit': {
      title: 'Audit',
      owner: 'Payments and administration',
      detail: 'The audit viewer is supplied by the payments workspace.'
    }
  };

  var TITLES = {
    login: 'Sign in',
    register: 'Register',
    'password-recovery': 'Reset password',
    'totp-setup': 'Set up Microsoft Authenticator',
    'totp-verify': 'Authenticator code',
    'session-expired': 'Session ended',
    dashboard: 'Home',
    profile: 'Profile',
    notifications: 'Notices',
    deposits: 'Fixed & recurring deposits',
    forex: 'Foreign exchange'
  };

  function hasRole(session, role) {
    return !!(session && Array.isArray(session.roles) && session.roles.indexOf(role) >= 0);
  }

  function isAuthenticated(session, now) {
    return !!(session && session.accessToken && typeof session.expiresAt === 'number' && session.expiresAt > now);
  }

  function home(session) {
    if (hasRole(session, 'CUSTOMER')) {
      return 'dashboard';
    }
    if (hasRole(session, 'ADMIN')) {
      return 'admin';
    }
    return 'login';
  }

  function desk(path) {
    return CUSTOMER.indexOf(path) >= 0 || ADMIN.indexOf(path) >= 0;
  }

  function evaluate(path, session, now) {
    var requested = path || 'login';
    var authed = isAuthenticated(session, now);
    var destination = home(session);

    if (requested === '' || PUBLIC.indexOf(requested) >= 0) {
      var signedInEntry = requested === 'login' || requested === 'register' || requested === 'session-expired'
        || requested === 'totp-setup' || requested === 'totp-verify' || requested === 'password-recovery' || requested === '';
      if (authed && signedInEntry && destination !== 'login') {
        return { path: destination, desk: true };
      }
      var publicPath = requested === '' ? 'login' : requested;
      return { path: publicPath, desk: false };
    }

    if (!authed) {
      return { path: 'login', desk: false };
    }

    if (ADMIN.indexOf(requested) >= 0) {
      if (!hasRole(session, 'ADMIN')) {
        return { path: destination === 'login' ? 'dashboard' : destination, desk: destination !== 'login' };
      }
      return { path: requested, desk: true };
    }

    if (CUSTOMER.indexOf(requested) >= 0) {
      if (!hasRole(session, 'CUSTOMER')) {
        return { path: destination, desk: destination !== 'login' };
      }
      return { path: requested, desk: true };
    }

    return { path: destination, desk: destination !== 'login' };
  }

  function navFor(session) {
    return {
      customer: hasRole(session, 'CUSTOMER') ? CUSTOMER_NAV.slice() : [],
      admin: hasRole(session, 'ADMIN') ? ADMIN_NAV.slice() : []
    };
  }

  function featureFor(path) {
    return FEATURES[path] || {
      title: 'Banking service',
      owner: 'A connected workspace',
      detail: 'This screen is part of Internet Banking and is supplied by its owning workspace.'
    };
  }

  function titleFor(path) {
    if (TITLES[path]) {
      return TITLES[path];
    }
    var feature = FEATURES[path];
    return feature ? feature.title : 'Internet Banking';
  }

  function routerConfig() {
    var routes = [{ path: '', redirect: 'login' }];
    PUBLIC.concat(CUSTOMER, ADMIN).forEach(function (path) {
      routes.push({ path: path });
    });
    return routes;
  }

  return {
    PUBLIC: PUBLIC,
    CUSTOMER: CUSTOMER,
    ADMIN: ADMIN,
    evaluate: evaluate,
    home: home,
    navFor: navFor,
    featureFor: featureFor,
    titleFor: titleFor,
    routerConfig: routerConfig,
    desk: desk
  };
});
