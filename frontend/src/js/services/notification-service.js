define([], function () {
  function NotificationService(apiClient) {
    this.list = function (page, size) {
      var index = page == null ? 0 : page;
      var limit = size == null ? 20 : size;
      return apiClient.get('/api/v1/notifications?page=' + encodeURIComponent(index) + '&size=' + encodeURIComponent(limit));
    };

    this.get = function (notificationId) {
      return apiClient.get('/api/v1/notifications/' + encodeURIComponent(notificationId));
    };

    this.unreadCount = function () {
      return apiClient.get('/api/v1/notifications/unread-count').then(function (result) {
        return Number(result && result.unread) || 0;
      });
    };

    this.markRead = function (notificationId) {
      return apiClient.patch('/api/v1/notifications/' + encodeURIComponent(notificationId) + '/read');
    };

    this.markAllRead = function () {
      return apiClient.patch('/api/v1/notifications/read-all');
    };
  }

  return NotificationService;
});
