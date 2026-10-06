/**
  Copyright (c) 2015, 2026, Oracle and/or its affiliates.
  Licensed under The Universal Permissive License (UPL), Version 1.0
  as shown at https://oss.oracle.com/licenses/upl/
*/

'use strict';

const http = require('http');

/**
 * Same-origin proxy so the Oracle JET dev server can call the API gateway
 * without a browser preflight. The gateway does not accept OPTIONS.
 */
function proxyApi(req, res, next) {
  if (!req.url || req.url.indexOf('/api/') !== 0) {
    next();
    return;
  }
  const headers = Object.assign({}, req.headers);
  delete headers.host;
  const upstream = http.request(
    {
      hostname: process.env.NET_BANKING_API_HOST || '127.0.0.1',
      port: Number(process.env.NET_BANKING_API_PORT || 8080),
      path: req.url,
      method: req.method,
      headers: headers
    },
    function (upstreamResponse) {
      res.writeHead(upstreamResponse.statusCode || 502, upstreamResponse.headers);
      upstreamResponse.pipe(res);
    }
  );
  upstream.on('error', function () {
    if (res.headersSent) {
      res.end();
      return;
    }
    const body = JSON.stringify({
      timestamp: new Date().toISOString(),
      status: 502,
      code: 'UPSTREAM_REQUEST_FAILED',
      message: 'Internet Banking is unavailable. Try again shortly.',
      path: req.url,
      correlationId: '',
      fieldErrors: {}
    });
    res.writeHead(502, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    res.end(body);
  });
  req.pipe(upstream);
}

module.exports = function (configObj) {
  return new Promise((resolve) => {
    console.log('Running before_serve hook.');
    configObj.preMiddleware = [proxyApi].concat(configObj.preMiddleware || []);
    resolve(configObj);
  });
};
