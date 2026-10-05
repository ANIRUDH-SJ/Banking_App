define(['../accUtils'], function (accUtils) {
  function FeaturePendingViewModel(params) {
    var options = params || {};
    this.title = options.title || 'Banking service';
    this.owner = options.owner || 'A connected workspace';
    this.detail = options.detail || 'This screen is supplied by its owning workspace.';
    this.featurePath = options.featurePath || '';
    this.connected = function () {
      document.title = this.title + ' | Internet Banking';
      accUtils.announce(this.title + '.', 'polite');
    };
  }

  return FeaturePendingViewModel;
});
