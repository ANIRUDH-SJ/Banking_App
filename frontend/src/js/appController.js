/**
 * @license
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(['knockout', 'ojs/ojcontext', 'ojs/ojknockouttemplateutils', 'ojs/ojcorerouter', 'ojs/ojmodulerouter-adapter', 'ojs/ojknockoutrouteradapter', 'ojs/ojurlparamadapter', 'ojs/ojresponsiveutils', 'ojs/ojresponsiveknockoututils', 'ojs/ojarraydataprovider',
        './app-context', 'ojs/ojdrawerpopup', 'ojs/ojmodule-element', 'ojs/ojknockout'],
  function(ko, Context, KnockoutTemplateUtils, CoreRouter, ModuleRouterAdapter, KnockoutRouterAdapter, UrlParamAdapter, ResponsiveUtils, ResponsiveKnockoutUtils, ArrayDataProvider, app) {
     'use strict';

     function ControllerViewModel() {
      var self = this;
      this.KnockoutTemplateUtils = KnockoutTemplateUtils;

      this.manner = ko.observable('polite');
      this.message = ko.observable();
      var announcementHandler = function (event) {
          self.message(event.detail.message);
          self.manner(event.detail.manner);
      };

      document.getElementById('globalBody').addEventListener('announce', announcementHandler, false);

      const smQuery = ResponsiveUtils.getFrameworkQuery(ResponsiveUtils.FRAMEWORK_QUERY_KEY.SM_ONLY);
      this.smScreen = ResponsiveKnockoutUtils.createMediaQueryObservable(smQuery);
      const mdQuery = ResponsiveUtils.getFrameworkQuery(ResponsiveUtils.FRAMEWORK_QUERY_KEY.MD_UP);
      this.mdScreen = ResponsiveKnockoutUtils.createMediaQueryObservable(mdQuery);

      var navData = [
        { path: '', redirect: 'dashboard' },
        { path: 'login' },
        { path: 'register' },
        { path: 'password-reset' },
        { path: 'totp-setup' },
        { path: 'totp-verify' },
        { path: 'dashboard' },
        { path: 'profile' },
        { path: 'notifications' },
        { path: 'admin' }
      ];

      var router = new CoreRouter(navData, {
        urlAdapter: new UrlParamAdapter()
      });
      app.setRouter(router);

      this.moduleAdapter = new ModuleRouterAdapter(router);
      this.selection = new KnockoutRouterAdapter(router);

      this.navItems = ko.observableArray([]);
      this.navDataProvider = new ArrayDataProvider(this.navItems, { keyAttributes: 'path' });
      this.hasNavigation = ko.pureComputed(function () {
        return self.navItems().length > 0;
      });

      this.sideDrawerOn = ko.observable(false);
      this.mdScreen.subscribe(function () { self.sideDrawerOn(false); });
      this.toggleDrawer = function () {
        self.sideDrawerOn(!self.sideDrawerOn());
      };

      this.appName = ko.observable('Internet Banking');
      this.displayName = ko.observable('');
      this.signedIn = ko.observable(false);
      this.sessionWarning = ko.observable(false);

      function activeSession() {
        var record = app.session.read();
        return record && record.expiresAt > Date.now() ? record : null;
      }

      function refreshIdentity() {
        var record = activeSession();
        self.signedIn(!!record);
        self.displayName(record ? record.username : '');
        self.navItems(app.routeGuard.navigationFor(record));
      }

      function enforce(path) {
        var decision = app.routeGuard.evaluate(path, activeSession(), app.flowState.current());
        if (!decision.allow && decision.redirect && decision.redirect !== path) {
          if (decision.reason === 'forbidden') {
            app.notices.set('You do not have access to that page.');
          }
          app.go(decision.redirect);
          return false;
        }
        return decision.allow;
      }

      app.setGuard(enforce);
      app.session.on('change', refreshIdentity);
      app.session.on('warning', function () {
        self.sessionWarning(true);
      });
      app.session.on('expired', function () {
        self.sessionWarning(false);
        app.flowState.clear();
        app.notices.set('Your session ended. Sign in again.');
        refreshIdentity();
        app.go('login');
      });
      app.session.on('unauthorized', function () {
        self.sessionWarning(false);
        app.flowState.clear();
        app.notices.set('Your session is no longer valid. Sign in again.');
        refreshIdentity();
        app.go('login');
      });

      router.currentState.subscribe(function (change) {
        var state = change && (change.state || change);
        var path = state && state.path ? state.path : '';
        if (path) {
          enforce(path);
        }
      });

      this.dismissSessionWarning = function () {
        self.sessionWarning(false);
      };

      this.signOut = function () {
        self.sessionWarning(false);
        app.flowState.clear();
        app.session.clear();
        refreshIdentity();
        app.go('login');
      };

      this.goToLogin = function () {
        app.go('login');
      };

      this.goToRegister = function () {
        app.go('register');
      };

      this.footerLinks = [
        { name: 'About Oracle', linkId: 'aboutOracle', linkTarget: 'http://www.oracle.com/us/corporate/index.html#menu-about' },
        { name: 'Contact Us', id: 'contactUs', linkTarget: 'http://www.oracle.com/us/corporate/contact/index.html' },
        { name: 'Legal Notices', id: 'legalNotices', linkTarget: 'http://www.oracle.com/us/legal/index.html' },
        { name: 'Terms Of Use', id: 'termsOfUse', linkTarget: 'http://www.oracle.com/us/legal/terms/index.html' },
        { name: 'Your Privacy Rights', id: 'yourPrivacyRights', linkTarget: 'http://www.oracle.com/us/legal/privacy/index.html' }
      ];

      refreshIdentity();
      app.session.start(1000);
      app.session.tick();
      router.sync();
     }

     Context.getPageContext().getBusyContext().applicationBootstrapComplete();

     return new ControllerViewModel();
  }
);
