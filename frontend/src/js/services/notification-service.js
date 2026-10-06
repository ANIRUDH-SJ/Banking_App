define([], function () {
  function NotificationService(apiClient) {
    this.list = function (page, size) {
      var index = page == null ? 0 : page;
      var limit = size == null ? 20 : size;
      return apiClient.get('/api/v1/notifications?page=' + encodeURIComponent(index) + '&size=' + encodeURIComponent(limit));
    };

    this.markRead = function (notificationId) {
      return apiClient.patch('/api/v1/notifications/' + encodeURIComponent(notificationId) + '/read');
    };
  }

  return NotificationService;
});
