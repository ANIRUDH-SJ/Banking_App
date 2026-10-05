define([], function () {
  'use strict';

  var routes = {
    login: { access: 'anonymous' },
    register: { access: 'anonymous' },
    'password-reset': { access: 'anonymous' },
    'totp-setup': { access: 'pending-setup' },
    'totp-verify': { access: 'pending-verify' },
    dashboard: { access: 'authenticated' },
    profile: { access: 'authenticated' },
    notifications: { access: 'authenticated' },
    admin: { access: 'role', roles: ['ADMIN'] }
  };

  function registerRoute(path, rule) {
    routes[path] = rule;
  }

  function hasRole(session, role) {
    return !!(session && Array.isArray(session.roles) && session.roles.indexOf(role) !== -1);
  }

  function evaluate(path, session, pending) {
    var rule = routes[path] || { access: 'authenticated' };
    var signedIn = !!(session && session.accessToken);
    if (rule.access === 'anonymous') {
      return signedIn ? { allow: false, redirect: 'dashboard' } : { allow: true };
    }
    if (rule.access === 'pending-setup') {
      return pending && pending.kind === 'setup' && pending.credentials
        ? { allow: true }
        : { allow: false, redirect: 'login' };
    }
    if (rule.access === 'pending-verify') {
      return pending && pending.kind === 'verify' && pending.challengeId
        ? { allow: true }
        : { allow: false, redirect: 'login' };
    }
    if (!signedIn) {
      return { allow: false, redirect: 'login', reason: 'auth' };
    }
    if (rule.access === 'role') {
      var allowed = (rule.roles || []).some(function (role) {
        return hasRole(session, role);
      });
      return allowed
        ? { allow: true }
        : { allow: false, redirect: 'dashboard', reason: 'forbidden' };
    }
    return { allow: true };
  }

  function navigationFor(session) {
    if (!session || !session.accessToken) {
      return [];
    }
    var items = [
      { path: 'dashboard', detail: { label: 'Dashboard', iconClass: 'oj-ux-ico-bar-chart' } },
      { path: 'profile', detail: { label: 'Profile', iconClass: 'oj-ux-ico-contact' } },
      { path: 'notifications', detail: { label: 'Notifications', iconClass: 'oj-ux-ico-notification' } }
    ];
    if (hasRole(session, 'ADMIN')) {
      items.push({ path: 'admin', detail: { label: 'Administration', iconClass: 'oj-ux-ico-settings' } });
    }
    return items;
  }

  return {
    registerRoute: registerRoute,
    evaluate: evaluate,
    navigationFor: navigationFor
  };
});
