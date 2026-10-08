define([
  'knockout',
  'ojs/ojcontext',
  'ojs/ojcorerouter',
  'ojs/ojurlparamadapter',
  'ojs/ojmodule-element-utils',
  'ojs/ojresponsiveutils',
  'ojs/ojresponsiveknockoututils',
  './services/registry',
  './services/format',
  'ojs/ojknockout',
  'ojs/ojbutton',
  'ojs/ojdialog',
  'ojs/ojdrawerpopup',
  'ojs/ojmodule-element'
], function (ko, Context, CoreRouter, UrlParamAdapter, moduleUtils, ResponsiveUtils, ResponsiveKnockoutUtils, registry, format) {
  function ControllerViewModel() {
    var self = this;
    var loadToken = 0;
    var warnedFor = 0;
    var leaving = false;
    var identityLoaded = false;
    var unreadTicket = 0;

    this.currentPath = ko.observable('login');
    this.showDesk = ko.observable(false);
    this.customerNav = ko.observableArray([]);
    this.adminNav = ko.observableArray([]);
    var navSections = [
      { id: 'overview', label: 'Overview', paths: ['dashboard'] },
      { id: 'accounts', label: 'Accounts', paths: ['accounts', 'transactions', 'beneficiaries', 'transfer'] },
      { id: 'payments', label: 'Payments', paths: ['billers', 'bill-payments'] },
      { id: 'products', label: 'Products', paths: ['cards', 'loans', 'deposits', 'forex'] },
      { id: 'profile', label: 'Profile', paths: ['profile', 'notifications'] }
    ];
    this.navGroups = ko.pureComputed(function () {
      var byPath = {};
      self.customerNav().concat(self.adminNav()).forEach(function (item) {
        byPath[item.path] = item;
      });
      var groups = navSections.map(function (section) {
        return {
          id: section.id,
          label: section.label,
          items: section.paths.map(function (path) {
            return byPath[path];
          }).filter(Boolean)
        };
      });
      if (self.adminNav().length) {
        groups.push({ id: 'admin', label: 'Administration', items: self.adminNav() });
      }
      return groups.filter(function (group) {
        return group.items.length;
      });
    });
    this.currentGroup = ko.pureComputed(function () {
      var path = self.currentPath();
      var groups = self.navGroups();
      for (var i = 0; i < groups.length; i += 1) {
        for (var j = 0; j < groups[i].items.length; j += 1) {
          if (groups[i].items[j].path === path) {
            return groups[i];
          }
        }
      }
      return groups[0] || { id: '', label: '', items: [] };
    });
    this.openGroup = function (group) {
      return self.open(group.items[0].path);
    };
    this.initials = ko.pureComputed(function () {
      var words = String(self.customerLabel() || '').trim().split(/\s+/).filter(Boolean);
      return words.slice(0, 2).map(function (word) {
        return word.charAt(0).toUpperCase();
      }).join('');
    });
    this.accessStep = ko.pureComputed(function () {
      var path = self.currentPath();
      if (path === 'totp-setup' || path === 'totp-verify') {
        return 2;
      }
      return path === 'login' ? 1 : 0;
    });
    this.customerLabel = ko.observable('');
    this.sessionLabel = ko.observable('');
    this.unreadCount = ko.observable(0);
    this.sideDrawerOn = ko.observable(false);
    this.moduleConfig = ko.observable({ view: [], viewModel: { connected: function () {} } });

    var smQuery = ResponsiveUtils.getFrameworkQuery(ResponsiveUtils.FRAMEWORK_QUERY_KEY.SM_ONLY);
    this.smScreen = ResponsiveKnockoutUtils.createMediaQueryObservable(smQuery);
    var mdQuery = ResponsiveUtils.getFrameworkQuery(ResponsiveUtils.FRAMEWORK_QUERY_KEY.MD_UP);
    this.mdScreen = ResponsiveKnockoutUtils.createMediaQueryObservable(mdQuery);
    this.mdScreen.subscribe(function () {
      self.sideDrawerOn(false);
    });

    this.manner = ko.observable('polite');
    this.message = ko.observable();
    document.getElementById('globalBody').addEventListener('announce', function (event) {
      self.message(event.detail.message);
      self.manner(event.detail.manner);
    }, false);

    var router = new CoreRouter(registry.routeGuard.routerConfig(), {
      urlAdapter: new UrlParamAdapter()
    });
    registry.setRouter(router);

    this.open = function (path) {
      self.sideDrawerOn(false);
      registry.go(path);
      return false;
    };

    this.goHome = function () {
      var session = registry.session.getSession();
      self.open(session ? registry.routeGuard.home(session) : 'login');
      return false;
    };

    this.toggleDrawer = function () {
      self.sideDrawerOn(!self.sideDrawerOn());
    };

    this.openNotices = function () {
      self.open('notifications');
      return false;
    };

    this.dismissWarning = function () {
      closeWarning();
    };

    this.signOut = function () {
      leaving = true;
      closeWarning();
      registry.session.clear();
      registry.authFlow.clear();
      self.customerLabel('');
      self.unreadCount(0);
      self.sessionLabel('');
      identityLoaded = false;
      self.sideDrawerOn(false);
      registry.go('login');
    };

    registry.apiClient.setUnauthorizedHandler(function () {
      if (leaving) {
        return;
      }
      closeWarning();
      registry.authFlow.clear();
      self.customerLabel('');
      self.unreadCount(0);
      identityLoaded = false;
      if (self.currentPath() !== 'login' && self.currentPath() !== 'session-expired') {
        registry.go('session-expired');
      }
    });

    registry.events.on('notifications-changed', function (change) {
      if (change && typeof change.unread === 'number') {
        self.unreadCount(Math.max(0, change.unread));
      } else if (change && change.read) {
        self.unreadCount(Math.max(0, self.unreadCount() - change.read));
      }
      refreshUnread();
    });
    registry.events.on('profile-changed', function (profile) {
      if (profile && profile.firstName) {
        self.customerLabel((profile.firstName + ' ' + profile.lastName).trim());
      }
    });

    function closeWarning() {
      var node = document.getElementById('sessionWarning');
      if (node && node.close) {
        node.close();
      }
    }

    function openWarning() {
      var node = document.getElementById('sessionWarning');
      if (node && node.open) {
        node.open();
      }
    }

    function refreshUnread() {
      if (!registry.session.isAuthenticated()) {
        self.unreadCount(0);
        return;
      }
      var ticket = ++unreadTicket;
      registry.notifications.unreadCount().then(function (unread) {
        if (ticket === unreadTicket) {
          self.unreadCount(unread);
        }
      }).catch(function () {
        return undefined;
      });
    }

    function refreshIdentity() {
      if (!registry.session.isAuthenticated()) {
        self.customerLabel('');
        self.unreadCount(0);
        return;
      }
      registry.profile.get().then(function (profile) {
        if (profile) {
          self.customerLabel(([profile.firstName, profile.lastName].filter(Boolean).join(' ')));
        }
      }).catch(function () {
        var session = registry.session.getSession();
        self.customerLabel(session ? session.username : '');
      });
      refreshUnread();
    }

    function showModule(path) {
      var token = ++loadToken;
      var feature = registry.routeGuard.featureFor(path);
      moduleUtils.createConfig({ name: path }).then(function (config) {
        if (token === loadToken && config) {
          self.moduleConfig(config);
        }
      }).catch(function (error) {
        var shellScreen = {
          login: true,
          register: true,
          'password-recovery': true,
          'totp-setup': true,
          'totp-verify': true,
          'session-expired': true,
          dashboard: true,
          profile: true,
          notifications: true
        };
        if (shellScreen[path] && typeof console !== 'undefined') {
          console.error('Unable to open ' + path, error && error.message ? error.message : error);
        }
        if (token !== loadToken) {
          return;
        }
        moduleUtils.createConfig({
          name: 'feature-pending',
          params: {
            title: feature.title,
            owner: feature.owner,
            detail: feature.detail,
            featurePath: path
          }
        }).then(function (config) {
          if (token === loadToken && config) {
            self.moduleConfig(config);
          }
        });
      });
    }

    function applyRoute(decision) {
      var enteringDesk = decision.desk && !self.showDesk();
      self.showDesk(decision.desk);
      self.currentPath(decision.path);
      document.title = registry.routeGuard.titleFor(decision.path) + ' | Internet Banking';
      if (!decision.desk) {
        leaving = false;
        self.customerNav([]);
        self.adminNav([]);
      } else {
        var nav = registry.routeGuard.navFor(registry.session.getSession());
        self.customerNav(nav.customer);
        self.adminNav(nav.admin);
        if (enteringDesk || !identityLoaded) {
          identityLoaded = true;
          refreshIdentity();
        }
      }
      showModule(decision.path);
    }

    router.currentState.subscribe(function (args) {
      if (!args || !args.state) {
        if (args && args.complete) {
          args.complete(Promise.resolve());
        }
        return;
      }
      var requested = args.state.path || 'login';
      var decision = registry.routeGuard.evaluate(requested, registry.session.getSession(), Date.now());
      if (decision.path !== requested) {
        args.complete(Promise.resolve());
        window.setTimeout(function () {
          router.go({ path: decision.path });
        }, 0);
        return;
      }
      applyRoute(decision);
      args.complete(Promise.resolve());
    });

    router.sync();

    window.setInterval(function () {
      var current = registry.session.getSession();
      if (!current) {
        self.sessionLabel('');
        warnedFor = 0;
        if (self.showDesk() && !leaving) {
          closeWarning();
          registry.authFlow.clear();
          identityLoaded = false;
          registry.go('session-expired');
        }
        return;
      }
      self.sessionLabel('Session ends ' + format.formatTime(current.expiresAt));
      if (current.expiresAt - Date.now() <= 60000 && warnedFor !== current.expiresAt) {
        warnedFor = current.expiresAt;
        openWarning();
      }
    }, 1000);

    Context.getPageContext().getBusyContext().applicationBootstrapComplete();
  }

  return new ControllerViewModel();
});
