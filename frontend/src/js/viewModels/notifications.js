define([
  'knockout',
  '../accUtils',
  '../app-context',
  'ojs/ojbutton'
], function (ko, accUtils, app) {
  'use strict';

  function NotificationsViewModel() {
    var self = this;
    var pageSize = 20;
    self.loading = ko.observable(true);
    self.formError = ko.observable('');
    self.items = ko.observableArray([]);
    self.page = ko.observable(0);
    self.totalPages = ko.observable(0);
    self.totalElements = ko.observable(0);
    self.empty = ko.pureComputed(function () {
      return !self.loading() && !self.formError() && self.items().length === 0;
    });
    self.hasPrevious = ko.pureComputed(function () {
      return self.page() > 0;
    });
    self.hasNext = ko.pureComputed(function () {
      return self.totalPages() > self.page() + 1;
    });

    function load(page) {
      self.loading(true);
      self.formError('');
      app.notifications.list(page, pageSize).then(function (response) {
        var content = (response && response.content) || [];
        self.items(content.map(function (item) {
          var row = {
            notificationId: item.notificationId,
            title: item.title,
            message: item.message,
            read: item.read,
            createdAt: item.createdAt,
            statusLabel: item.read ? 'Read' : 'Unread'
          };
          row.markRead = function () {
            self.markRead(row);
          };
          return row;
        }));
        self.page(response ? response.page : page);
        self.totalPages(response ? response.totalPages : 0);
        self.totalElements(response ? response.totalElements : 0);
      }).catch(function (error) {
        self.items([]);
        self.formError(app.errorMessage.message(error, 'Notifications could not be loaded.'));
      }).finally(function () {
        self.loading(false);
      });
    }

    self.markRead = function (item) {
      if (!item || item.read) {
        return;
      }
      app.notifications.markRead(item.notificationId).then(function () {
        self.items(self.items().map(function (current) {
          if (current.notificationId !== item.notificationId) {
            return current;
          }
          var row = {
            notificationId: current.notificationId,
            title: current.title,
            message: current.message,
            read: true,
            createdAt: current.createdAt,
            statusLabel: 'Read'
          };
          row.markRead = current.markRead;
          return row;
        }));
        accUtils.announce('Notification marked as read.', 'polite');
      }).catch(function (error) {
        self.formError(app.errorMessage.message(error, 'The notification could not be updated.'));
      });
    };

    self.previousPage = function () {
      if (self.hasPrevious()) {
        load(self.page() - 1);
      }
    };

    self.nextPage = function () {
      if (self.hasNext()) {
        load(self.page() + 1);
      }
    };

    this.connected = function () {
      if (!app.ensure('notifications')) {
        return;
      }
      accUtils.announce('Notifications page loaded.', 'assertive');
      document.title = 'Notifications';
      load(0);
    };
  }

  return NotificationsViewModel;
});
