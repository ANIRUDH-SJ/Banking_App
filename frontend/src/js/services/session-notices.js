define([], function () {
  'use strict';

  var notice = '';

  return {
    set: function (message) {
      notice = message || '';
    },
    consume: function () {
      var message = notice;
      notice = '';
      return message;
    }
  };
});
