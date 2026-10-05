define([], function () {
  'use strict';

  function NotificationService(apiClient) {
    this.apiClient = apiClient;
  }

  NotificationService.prototype.list = function (page, size) {
    var query = '?page=' + encodeURIComponent(page || 0) + '&size=' + encodeURIComponent(size || 20);
    return this.apiClient.get('/api/v1/notifications' + query);
  };

  NotificationService.prototype.markRead = function (notificationId) {
    return this.apiClient.patch('/api/v1/notifications/' + encodeURIComponent(notificationId) + '/read');
  };

  return NotificationService;
});
