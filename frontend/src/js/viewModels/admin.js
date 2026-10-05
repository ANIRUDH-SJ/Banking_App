define([
  'knockout',
  '../accUtils',
  '../app-context'
], function (ko, accUtils, app) {
  'use strict';

  function AdminViewModel() {
    var self = this;
    self.username = ko.observable('');

    this.connected = function () {
      if (!app.ensure('admin')) {
        return;
      }
      var session = app.session.current();
      self.username(session ? session.username : '');
      accUtils.announce('Administration page loaded.', 'assertive');
      document.title = 'Administration';
    };
  }

  return AdminViewModel;
});
