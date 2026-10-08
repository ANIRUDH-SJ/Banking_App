define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/format',
  '../services/ui-support',
  'oj-c/button',
  'oj-c/badge',
  'oj-c/skeleton'
], function (ko, accUtils, registry, format, ui) {
  var GLYPHS = [
    [/LOGIN|SECURITY|PASSWORD|TOTP|PIN|CARD/, 'i-shield'],
    [/STATEMENT/, 'i-statement'],
    [/TRANSFER|PAYMENT|BILL/, 'i-payments'],
    [/FOREX/, 'i-forex'],
    [/LOAN|EMI/, 'i-loans'],
    [/DEPOSIT/, 'i-deposits']
  ];

  function NotificationsViewModel() {
    var self = this;
    var generation = 0;
    var opening = 0;
    self.loading = ko.observable(true);
    self.problem = new ui.Problem();
    self.notices = ko.observableArray([]);
    self.page = ko.observable(0);
    self.last = ko.observable(true);
    self.selected = ko.observable(null);
    self.opening = ko.observable(false);
    self.markingAll = ko.observable(false);
    self.when = format.formatDateTime;
    self.label = format.labelize;

    self.unreadHere = ko.pureComputed(function () {
      return self.notices().filter(function (item) { return !item.read; }).length;
    });

    self.glyph = function (notice) {
      var type = String((notice && notice.notificationType) || (notice && notice.title) || '').toUpperCase();
      for (var i = 0; i < GLYPHS.length; i += 1) {
        if (GLYPHS[i][0].test(type)) {
          return GLYPHS[i][1];
        }
      }
      return 'i-bell';
    };

    self.isSelected = function (notice) {
      var current = self.selected();
      return !!current && current.notificationId === notice.notificationId;
    };

    function replace(updated) {
      self.notices(self.notices().map(function (item) {
        return item.notificationId === updated.notificationId ? Object.assign({}, item, updated) : item;
      }));
    }

    function markRead(notice) {
      registry.notifications.markRead(notice.notificationId).then(function () {
        var read = Object.assign({}, notice, { read: true, readAt: notice.readAt || new Date().toISOString() });
        replace(read);
        if (self.isSelected(notice)) {
          self.selected(read);
        }
        registry.events.emit('notifications-changed', { read: 1 });
      }).catch(function (error) {
        self.problem.set(error, 'The notice could not be marked as read.');
      });
    }

    /** Opening a notice shows it in full and marks it read. */
    self.open = function (notice) {
      if (!notice) {
        return;
      }
      var ticket = ++opening;
      self.problem.clear();
      self.selected(notice);
      self.opening(true);
      registry.notifications.get(notice.notificationId).then(function (full) {
        if (ticket !== opening) {
          return;
        }
        var merged = Object.assign({}, notice, full || {});
        self.selected(merged);
        replace(merged);
        if (!merged.read) {
          markRead(merged);
        }
      }).catch(function (error) {
        if (ticket === opening) {
          self.problem.set(error, 'The notice could not be opened.');
        }
      }).finally(function () {
        if (ticket === opening) {
          self.opening(false);
        }
      });
      window.setTimeout(function () {
        var heading = document.getElementById('notice-detail-heading');
        if (heading) {
          heading.focus();
        }
      }, 0);
    };

    self.close = function () {
      opening += 1;
      self.selected(null);
    };

    self.markAll = function () {
      if (self.markingAll()) {
        return;
      }
      self.markingAll(true);
      registry.notifications.markAllRead().then(function () {
        var stamp = new Date().toISOString();
        self.notices(self.notices().map(function (item) {
          return item.read ? item : Object.assign({}, item, { read: true, readAt: stamp });
        }));
        if (self.selected()) {
          self.selected(Object.assign({}, self.selected(), { read: true }));
        }
        registry.events.emit('notifications-changed', { unread: 0 });
        accUtils.announce('All notices marked as read.', 'polite');
      }).catch(function (error) {
        self.problem.set(error, 'Notices could not be updated.');
      }).finally(function () {
        self.markingAll(false);
      });
    };

    function load(page, preset) {
      var ticket = ++generation;
      self.loading(true);
      self.problem.clear();
      registry.notifications.list(page, 20).then(function (result) {
        if (ticket !== generation) {
          return;
        }
        var rows = (result && result.content) || [];
        self.notices(rows);
        self.page(result ? result.page : page);
        self.last(!result || result.last);
        var match = preset && rows.filter(function (item) { return String(item.notificationId) === String(preset); })[0];
        if (match) {
          self.open(match);
        }
      }).catch(function (error) {
        if (ticket !== generation) {
          return;
        }
        self.notices([]);
        self.problem.set(error, 'Notices could not be loaded.');
      }).finally(function () {
        if (ticket === generation) {
          self.loading(false);
        }
      });
    }

    self.older = function () {
      if (!self.last()) {
        self.close();
        load(self.page() + 1);
      }
    };

    self.newer = function () {
      if (self.page() > 0) {
        self.close();
        load(self.page() - 1);
      }
    };

    self.connected = function () {
      accUtils.announce('Notices.', 'polite');
      document.title = 'Notices | ORACLE INTERNATIONAL BANK (OIB)';
      self.selected(null);
      load(0, ui.take('notifications.id'));
    };

    self.disconnected = function () {
      generation += 1;
      opening += 1;
    };
  }

  return NotificationsViewModel;
});
