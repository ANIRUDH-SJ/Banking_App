define([], function () {
  'use strict';

  // Feature CSS is loaded once when a Member 3 route opens. Keeping it out of
  // app.css lets the foundation PR own the banking shell and design tokens.
  if (!document.getElementById('member3Styles')) {
    var sheet = document.createElement('link');
    sheet.id = 'member3Styles';
    sheet.rel = 'stylesheet';
    sheet.href = 'css/member3.css';
    document.head.appendChild(sheet);
  }
});
