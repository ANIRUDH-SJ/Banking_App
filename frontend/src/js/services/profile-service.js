define([], function () {
  'use strict';

  function ProfileService(apiClient) {
    this.apiClient = apiClient;
  }

  ProfileService.prototype.getProfile = function () {
    return this.apiClient.get('/api/v1/profile');
  };

  ProfileService.prototype.updateProfile = function (request) {
    return this.apiClient.put('/api/v1/profile', {
      firstName: String(request.firstName).trim(),
      lastName: String(request.lastName).trim(),
      mobileNumber: request.mobileNumber
    });
  };

  return ProfileService;
});
