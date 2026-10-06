define([], function () {
  'use strict';

  if (!document.getElementById('member2Styles')) {
    var sheet = document.createElement('link');
    sheet.id = 'member2Styles';
    sheet.rel = 'stylesheet';
    sheet.href = 'css/member2.css';
    document.head.appendChild(sheet);
  }
});
