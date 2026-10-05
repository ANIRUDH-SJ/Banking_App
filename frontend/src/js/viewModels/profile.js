define([
  'knockout',
  '../accUtils',
  '../app-context',
  'ojs/ojbutton',
  'ojs/ojinputtext',
  'ojs/ojlabel'
], function (ko, accUtils, app) {
  'use strict';

  function ProfileViewModel() {
    var self = this;
    self.loading = ko.observable(true);
    self.saving = ko.observable(false);
    self.formError = ko.observable('');
    self.success = ko.observable('');
    self.customerNumber = ko.observable('');
    self.dateOfBirth = ko.observable('');
    self.kycStatus = ko.observable('');
    self.activeLabel = ko.observable('');
    self.firstName = ko.observable('');
    self.lastName = ko.observable('');
    self.mobileNumber = ko.observable('');
    self.firstNameError = ko.observable('');
    self.lastNameError = ko.observable('');
    self.mobileError = ko.observable('');

    function applyProfile(profile) {
      self.customerNumber(profile.customerNumber || '');
      self.dateOfBirth(profile.dateOfBirth || '');
      self.kycStatus(profile.kycStatus || '');
      self.activeLabel(profile.active ? 'Active' : 'Inactive');
      self.firstName(profile.firstName || '');
      self.lastName(profile.lastName || '');
      self.mobileNumber(profile.mobileNumber || '');
    }

    self.save = function () {
      if (self.saving()) {
        return;
      }
      self.formError('');
      self.success('');
      var validation = app.validation.profile({
        firstName: self.firstName(),
        lastName: self.lastName(),
        mobileNumber: self.mobileNumber()
      });
      self.firstNameError(validation.fieldErrors.firstName || '');
      self.lastNameError(validation.fieldErrors.lastName || '');
      self.mobileError(validation.fieldErrors.mobileNumber || '');
      if (!validation.valid) {
        return;
      }
      self.saving(true);
      app.profile.updateProfile({
        firstName: self.firstName(),
        lastName: self.lastName(),
        mobileNumber: self.mobileNumber()
      }).then(function (profile) {
        applyProfile(profile);
        self.success('Profile updated.');
        accUtils.announce('Profile updated.', 'polite');
      }).catch(function (error) {
        self.firstNameError(app.errorMessage.field(error, 'firstName'));
        self.lastNameError(app.errorMessage.field(error, 'lastName'));
        self.mobileError(app.errorMessage.field(error, 'mobileNumber'));
        self.formError(app.errorMessage.message(error, 'The profile could not be updated.'));
      }).finally(function () {
        self.saving(false);
      });
    };

    this.connected = function () {
      if (!app.ensure('profile')) {
        return;
      }
      accUtils.announce('Profile page loaded.', 'assertive');
      document.title = 'Profile';
      self.loading(true);
      app.profile.getProfile().then(applyProfile).catch(function (error) {
        self.formError(app.errorMessage.message(error, 'Your profile could not be loaded.'));
      }).finally(function () {
        self.loading(false);
      });
    };
  }

  return ProfileViewModel;
});
