import test from 'node:test';
import assert from 'node:assert/strict';
import {chooseLocale,pathLocale} from '../src/locale.js';
test('browser language negotiation supports regions and falls back to English',()=>{assert.equal(chooseLocale(['it-IT']),'it');assert.equal(chooseLocale(['en-GB']),'en');assert.equal(chooseLocale(['de-DE']),'en');assert.equal(chooseLocale(['fr-FR','it-CH']),'it');assert.equal(chooseLocale([]),'en');});
test('explicit route wins over saved preference, which wins over browser',()=>{assert.equal(chooseLocale(['en-US'],'it'),'it');assert.equal(chooseLocale(['it-IT'],'it','en'),'en');assert.equal(chooseLocale(['it-IT'],'invalid','invalid'),'it');});
test('only complete supported route prefixes select a locale',()=>{assert.equal(pathLocale('/en/'),'en');assert.equal(pathLocale('/it'),'it');assert.equal(pathLocale('/english/'),undefined);assert.equal(pathLocale('/'),undefined);});
