define([], function () {
  function ProfileService(apiClient) {
    this.get = function () {
      return apiClient.get('/api/v1/profile');
    };

    this.update = function (request) {
      return apiClient.put('/api/v1/profile', {
        firstName: request.firstName,
        lastName: request.lastName,
        mobileNumber: request.mobileNumber
      });
    };
  }

  return ProfileService;
});
