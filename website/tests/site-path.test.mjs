import test from 'node:test';
import assert from 'node:assert/strict';
import {sitePath} from '../src/site-path.js';
import {pathLocale} from '../src/locale.js';
test('project deployment keeps assets and locale routes under its prefix',()=>{
  assert.equal(sitePath('/assets/cover.png','/Songlass/'),'/Songlass/assets/cover.png');
  assert.equal(sitePath('it/','/Songlass/'),'/Songlass/it/');
  assert.equal(pathLocale('/Songlass/it/','/Songlass/'),'it');
  assert.equal(pathLocale('/Songlass/en/','/Songlass/'),'en');
  assert.equal(pathLocale('/Other/it/','/Songlass/'),undefined);
});
