define([
  'knockout',
  '../accUtils',
  '../services/registry',
  '../services/format',
  '../services/ui-support',
  '../services/BillerService',
  'oj-c/button',
  'oj-c/input-text',
  'oj-c/skeleton'
], function (ko, accUtils, registry, format, ui, billers) {
  var GLYPHS = { ELECTRICITY: 'i-bolt', WATER: 'i-drop', MOBILE: 'i-phone', GAS: 'i-bolt' };

  function BillersViewModel() {
    var self = this;
    var generation = 0;

    self.problem = new ui.Problem();
    self.loading = ko.observable(true);
    self.billers = ko.observableArray([]);
    self.search = ko.observable('');
    self.searchRaw = ko.observable('');

    self.groups = ko.pureComputed(function () {
      var term = String(self.searchRaw() || '').trim().toLowerCase();
      var byCategory = {};
      var order = [];
      self.billers().forEach(function (biller) {
        var haystack = (biller.name + ' ' + biller.category + ' ' + biller.code).toLowerCase();
        if (term && haystack.indexOf(term) < 0) {
          return;
        }
        var key = biller.category || 'OTHER';
        if (!byCategory[key]) {
          byCategory[key] = [];
          order.push(key);
        }
        byCategory[key].push(biller);
      });
      return order.map(function (key) {
        return { category: format.labelize(key), billers: byCategory[key] };
      });
    });

    self.glyph = function (biller) {
      return GLYPHS[String(biller && biller.code || '').toUpperCase()] || 'i-billers';
    };

    self.limits = function (biller) {
      if (biller.minAmount == null && biller.maxAmount == null) {
        return biller.referenceLabel || '';
      }
      return (biller.referenceLabel ? biller.referenceLabel + ' · ' : '') +
        format.formatMoney(biller.minAmount || 0, 'INR') + ' to ' + format.formatMoney(biller.maxAmount, 'INR');
    };

    self.pay = function (biller) {
      ui.hand('bill.billerId', biller.billerId);
      registry.go('bill-payments');
    };

    self.connected = function () {
      var ticket = ++generation;
      accUtils.announce('Billers.', 'polite');
      document.title = 'Billers | Internet Banking';
      self.loading(true);
      self.problem.clear();
      billers.listActive().then(function (list) {
        if (ticket === generation) {
          self.billers(format.asList(list));
        }
      }).catch(function (error) {
        if (ticket === generation) {
          self.problem.set(error, 'Billers could not be loaded.');
        }
      }).finally(function () {
        if (ticket === generation) {
          self.loading(false);
        }
      });
    };

    self.disconnected = function () {
      generation += 1;
    };
  }

  return BillersViewModel;
});
