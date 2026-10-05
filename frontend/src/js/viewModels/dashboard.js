define([
  'knockout',
  '../accUtils',
  '../app-context',
  'ojs/ojbutton'
], function (ko, accUtils, app) {
  'use strict';

  function DashboardViewModel() {
    var self = this;
    self.loading = ko.observable(true);
    self.profileError = ko.observable('');
    self.notificationError = ko.observable('');
    self.displayName = ko.observable('');
    self.customerNumber = ko.observable('');
    self.kycStatus = ko.observable('');
    self.active = ko.observable(false);
    self.notifications = ko.observableArray([]);
    self.summaries = ko.observableArray([]);
    self.hasSummaries = ko.pureComputed(function () {
      return self.summaries().length > 0;
    });

    function load() {
      self.loading(true);
      self.profileError('');
      self.notificationError('');
      var profileRequest = app.profile.getProfile().then(function (profile) {
        var name = [profile.firstName, profile.lastName].filter(Boolean).join(' ');
        self.displayName(name);
        self.customerNumber(profile.customerNumber || '');
        self.kycStatus(profile.kycStatus || '');
        self.active(!!profile.active);
      }).catch(function (error) {
        self.profileError(app.errorMessage.message(error, 'Your profile could not be loaded.'));
      });
      var notificationRequest = app.notifications.list(0, 5).then(function (page) {
        self.notifications((page && page.content) || []);
      }).catch(function (error) {
        self.notificationError(app.errorMessage.message(error, 'Notifications could not be loaded.'));
      });
      var summaryRequest = app.dashboardSlots.summaries().then(function (items) {
        self.summaries(items);
      });
      Promise.all([profileRequest, notificationRequest, summaryRequest]).finally(function () {
        self.loading(false);
      });
    }

    self.openNotifications = function () {
      app.go('notifications');
    };

    self.openProfile = function () {
      app.go('profile');
    };

    this.connected = function () {
      if (!app.ensure('dashboard')) {
        return;
      }
      accUtils.announce('Dashboard page loaded.', 'assertive');
      document.title = 'Dashboard';
      load();
    };
  }

  return DashboardViewModel;
});
