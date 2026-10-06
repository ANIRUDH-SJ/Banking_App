/**
 * @license
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
/*
 * Your application specific code will go here
 */
define(['knockout', 'ojs/ojcontext', 'ojs/ojmodule-element-utils', 'ojs/ojknockouttemplateutils', 'ojs/ojcorerouter', 'ojs/ojmodulerouter-adapter', 'ojs/ojknockoutrouteradapter', 'ojs/ojurlparamadapter', 'ojs/ojresponsiveutils', 'ojs/ojresponsiveknockoututils', 'ojs/ojarraydataprovider',
        'ojs/ojdrawerpopup', 'ojs/ojmodule-element', 'ojs/ojknockout', './services/AuthService'],
  function(ko, Context, moduleUtils, KnockoutTemplateUtils, CoreRouter, ModuleRouterAdapter, KnockoutRouterAdapter, UrlParamAdapter, ResponsiveUtils, ResponsiveKnockoutUtils, ArrayDataProvider, DrawerPopup, ModuleElement, Knockout, AuthService) {

     function ControllerViewModel() {

      this.KnockoutTemplateUtils = KnockoutTemplateUtils;

      // Handle announcements sent when pages change, for Accessibility.
      this.manner = ko.observable('polite');
      this.message = ko.observable();
      announcementHandler = (event) => {
          this.message(event.detail.message);
          this.manner(event.detail.manner);
      };

      document.getElementById('globalBody').addEventListener('announce', announcementHandler, false);


      // Media queries for responsive layouts
      const smQuery = ResponsiveUtils.getFrameworkQuery(ResponsiveUtils.FRAMEWORK_QUERY_KEY.SM_ONLY);
      this.smScreen = ResponsiveKnockoutUtils.createMediaQueryObservable(smQuery);
      const mdQuery = ResponsiveUtils.getFrameworkQuery(ResponsiveUtils.FRAMEWORK_QUERY_KEY.MD_UP);
      this.mdScreen = ResponsiveKnockoutUtils.createMediaQueryObservable(mdQuery);

      let navData = [
        { path: '', redirect: 'dashboard' },
        { path: 'dashboard', detail: { label: 'Dashboard', iconClass: 'oj-ux-ico-bar-chart' } },
        { path: 'incidents', detail: { label: 'Incidents', iconClass: 'oj-ux-ico-fire' } },
        { path: 'customers', detail: { label: 'Customers', iconClass: 'oj-ux-ico-contact-group' } },
        { path: 'billers', detail: { label: 'Billers', iconClass: 'oj-ux-ico-credit-card' } },
        { path: 'cards', detail: { label: 'Cards', iconClass: 'oj-ux-ico-credit-card' } },
        { path: 'loans', detail: { label: 'Loans', iconClass: 'oj-ux-ico-money' } },
        { path: 'admin', detail: { label: 'Administration', iconClass: 'oj-ux-ico-settings' } },
        { path: 'about', detail: { label: 'About', iconClass: 'oj-ux-ico-information-s' } }
      ];

      // Router setup
      let router = new CoreRouter(navData, {
        urlAdapter: new UrlParamAdapter()
      });
      router.sync();

      this.moduleAdapter = new ModuleRouterAdapter(router);

      this.selection = new KnockoutRouterAdapter(router);

      // Setup the navDataProvider with the routes, excluding the first redirected
      // route.
      this.navDataProvider = new ArrayDataProvider(navData.slice(1), {keyAttributes: "path"});

      // Drawer
      this.sideDrawerOn = ko.observable(false);

      // Close drawer on medium and larger screens
      this.mdScreen.subscribe(() => { this.sideDrawerOn(false) });

      // Called by navigation drawer toggle button and after selection of nav drawer item
      this.toggleDrawer = () => {
        this.sideDrawerOn(!this.sideDrawerOn());
      }

      // Header
      // Application Name used in Branding Area
      this.appName = ko.observable("App Name");
      // User Info used in Global Navigation area
      const currentIdentity = AuthService.getIdentity();
      this.isAuthenticated = ko.observable(Boolean(AuthService.getAccessToken()));
      this.userLogin = ko.observable(currentIdentity ? currentIdentity.username : 'Sign in');
      this.signInOpen = ko.observable(false);
      this.authStep = ko.observable('credentials');
      this.authUsername = ko.observable('');
      this.authPassword = ko.observable('');
      this.authCode = ko.observable('');
      this.authChallengeId = ko.observable('');
      this.authSetup = ko.observable(null);
      this.authBusy = ko.observable(false);
      this.authError = ko.observable('');
      this.signInOpen.subscribe((isOpen) => {
        if (!isOpen && !this.isAuthenticated()) {
          this.authPassword(''); this.authCode(''); this.authChallengeId(''); this.authSetup(null);
          this.authStep('credentials');
        }
      });

      this.openSignIn = () => {
        this.authStep('credentials'); this.authError(''); this.signInOpen(true);
      };

      this.beginSignIn = async () => {
        const credentials = {
          usernameOrEmail: this.authUsername().trim(),
          password: this.authPassword()
        };
        if (!credentials.usernameOrEmail || !credentials.password) {
          this.authError('Enter your username or email and password.'); return;
        }
        this.authBusy(true); this.authError('');
        try {
          const result = await AuthService.beginLogin(credentials);
          if (result.status === 'TOTP_SETUP_REQUIRED') {
            const setup = await AuthService.setupTotp(credentials);
            this.authSetup({
              qrCodeDataUri: setup.qrCodeDataUri,
              manualEntryKey: setup.manualEntryKey,
              accountName: setup.accountName
            });
            this.authCode(''); this.authStep('setup');
          } else if (result.status === 'TOTP_REQUIRED' && result.challengeId) {
            this.authChallengeId(result.challengeId); this.authPassword('');
            this.authCode(''); this.authStep('verify');
          } else {
            throw new Error('The server returned an unsupported sign-in response.');
          }
        } catch (error) { this.authError(error.message || 'Unable to sign in.'); }
        finally { this.authBusy(false); }
      };

      this.confirmAuthenticatorSetup = async () => {
        const code = this.authCode().trim();
        if (!/^\d{6}$/.test(code)) { this.authError('Enter the current six-digit authenticator code.'); return; }
        const credentials = {
          usernameOrEmail: this.authUsername().trim(),
          password: this.authPassword()
        };
        this.authBusy(true); this.authError('');
        try {
          await AuthService.confirmTotp(credentials, code);
          const challenge = await AuthService.beginLogin(credentials);
          if (challenge.status !== 'TOTP_REQUIRED' || !challenge.challengeId) {
            throw new Error('Authenticator setup succeeded, but sign-in could not be started. Try signing in again.');
          }
          this.authPassword(''); this.authSetup(null); this.authCode('');
          this.authChallengeId(challenge.challengeId); this.authStep('verify');
        } catch (error) { this.authError(error.message || 'Unable to confirm authenticator setup.'); }
        finally { this.authBusy(false); }
      };

      this.verifyAuthenticatorCode = async () => {
        const code = this.authCode().trim();
        if (!/^\d{6}$/.test(code)) { this.authError('Enter the current six-digit authenticator code.'); return; }
        this.authBusy(true); this.authError('');
        try {
          const result = await AuthService.verifyTotp(this.authChallengeId(), code);
          const identity = AuthService.setSession(result);
          this.isAuthenticated(true); this.userLogin(identity.username || 'Signed in');
          this.authPassword(''); this.authCode(''); this.authChallengeId(''); this.authSetup(null);
          this.signInOpen(false);
          window.dispatchEvent(new CustomEvent('netbanking:authenticated'));
        } catch (error) { this.authError(error.message || 'Authenticator verification failed.'); }
        finally { this.authBusy(false); }
      };

      this.signOut = () => {
        AuthService.clearSession(); this.isAuthenticated(false); this.userLogin('Sign in');
        this.authPassword(''); this.authCode(''); this.authChallengeId(''); this.authSetup(null);
        this.authStep('credentials'); this.signInOpen(false);
        window.dispatchEvent(new CustomEvent('netbanking:signed-out'));
      };

      this.handleUserMenu = (event) => {
        const action = event.detail && event.detail.selectedValue;
        if (action === 'signIn') this.openSignIn();
        if (action === 'out') this.signOut();
      };

      window.addEventListener('netbanking:unauthorized', () => {
        this.isAuthenticated(false); this.userLogin('Sign in'); this.openSignIn();
      });

      // Footer
      this.footerLinks = [
        {name: 'About Oracle', linkId: 'aboutOracle', linkTarget:'http://www.oracle.com/us/corporate/index.html#menu-about'},
        { name: "Contact Us", id: "contactUs", linkTarget: "http://www.oracle.com/us/corporate/contact/index.html" },
        { name: "Legal Notices", id: "legalNotices", linkTarget: "http://www.oracle.com/us/legal/index.html" },
        { name: "Terms Of Use", id: "termsOfUse", linkTarget: "http://www.oracle.com/us/legal/terms/index.html" },
        { name: "Your Privacy Rights", id: "yourPrivacyRights", linkTarget: "http://www.oracle.com/us/legal/privacy/index.html" },
      ];
     }
     // release the application bootstrap busy state
     Context.getPageContext().getBusyContext().applicationBootstrapComplete();

     return new ControllerViewModel();
  }
);
