'use strict';

const fs = require('fs');
const path = require('path');
const vm = require('vm');

function load(relativePath, registry) {
  const modules = registry || {};
  const filename = path.join(__dirname, '..', 'src', 'js', relativePath);
  const source = fs.readFileSync(filename, 'utf8');
  let exported;
  function define(dependencies, factory) {
    const resolved = dependencies.map(function (dependency) {
      if (dependency.startsWith('./')) {
        const next = path.posix.normalize(path.posix.join(path.posix.dirname(relativePath), dependency));
        if (!modules[next]) {
          modules[next] = load(next + '.js', modules);
        }
        return modules[next];
      }
      throw new Error('Unexpected dependency ' + dependency);
    });
    exported = factory.apply(null, resolved);
  }
  define.amd = true;
  vm.runInNewContext(source, { define: define, console: console, Buffer: Buffer, atob: atob, decodeURIComponent: decodeURIComponent }, { filename: filename });
  modules[relativePath.replace(/\.js$/, '')] = exported;
  return exported;
}

module.exports = load;
