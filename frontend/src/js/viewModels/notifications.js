define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/format',
  'oj-c/button',
  'oj-c/skeleton'
], function (ko, accUtils, registry, format) {
  function NotificationsViewModel() {
    var self = this;
    var generation = 0;
    self.loading = ko.observable(true);
    self.error = ko.observable('');
    self.reference = ko.observable('');
    self.notices = ko.observableArray([]);
    self.page = ko.observable(0);
    self.last = ko.observable(true);
    self.busyId = ko.observable(null);
    self.when = format.formatDateTime;

    function load(page) {
      var ticket = ++generation;
      self.loading(true);
      self.error('');
      self.reference('');
      registry.notifications.list(page, 20).then(function (result) {
        if (ticket !== generation) {
          return;
        }
        self.notices((result && result.content) || []);
        self.page(result ? result.page : page);
        self.last(!result || result.last);
      }).catch(function (error) {
        if (ticket !== generation) {
          return;
        }
        self.notices([]);
        self.error((error && error.message) || 'Notices could not be loaded.');
        if (error && error.correlationId) {
          self.reference('Reference ' + error.correlationId + '.');
        }
      }).finally(function () {
        if (ticket === generation) {
          self.loading(false);
        }
      });
    }

    self.markRead = function (notice) {
      if (!notice || notice.read || self.busyId()) {
        return;
      }
      self.busyId(notice.notificationId);
      registry.notifications.markRead(notice.notificationId).then(function () {
        self.notices(self.notices().map(function (item) {
          if (item.notificationId === notice.notificationId) {
            return Object.assign({}, item, { read: true });
          }
          return item;
        }));
        registry.events.emit('notifications-changed');
        accUtils.announce('Notice marked as read.', 'polite');
      }).catch(function (error) {
        self.error((error && error.message) || 'The notice could not be updated.');
      }).finally(function () {
        self.busyId(null);
      });
    };

    self.markReadAction = function (event, current, bindingContext) {
      self.markRead((bindingContext && bindingContext.$data) || (current && current.data) || current);
    };

    self.older = function () {
      if (!self.last()) {
        load(self.page() + 1);
      }
    };

    self.newer = function () {
      if (self.page() > 0) {
        load(self.page() - 1);
      }
    };

    self.connected = function () {
      accUtils.announce('Notices.', 'polite');
      document.title = 'Notices | Internet Banking';
      load(0);
    };

    self.disconnected = function () {
      generation += 1;
    };
  }

  return NotificationsViewModel;
});
