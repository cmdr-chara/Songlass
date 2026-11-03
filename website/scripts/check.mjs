import {readFile,access} from 'node:fs/promises';
import assert from 'node:assert/strict';
const {resolveConfig}=await import('vite');
const {base}=await resolveConfig({},'build');
const sections=['songlass_intro','style_showcase','appearance_options','music_library','playback_control','sound_control','lyrics_artwork','platform_details'];
for(const [route,language] of [['','en'],['en/','en'],['it/','it']]){
  const html=await readFile(`dist/${route}index.html`,'utf8');
  assert(html.includes(`<html lang="${language}"`));
  const ids=[...html.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]);
  assert.equal(new Set(ids).size,ids.length,'Duplicate IDs');
  for(const id of [...sections,'songlass_footer'])assert(ids.includes(id),`Missing section ${id}`);
  assert.equal((html.match(/<section\b/g)??[]).length,8);
  assert.equal((html.match(/data-style-choice/g)??[]).length,9);
  assert.equal((html.match(/<details\b/g)??[]).length,5);
  for(const m of html.matchAll(/href="#([^"]+)"/g))assert(ids.includes(m[1]),`Missing anchor ${m[1]}`);
  for(const m of html.matchAll(/(?:src|href)="(\/[^"]+)"/g)){
    const url=m[1];assert(url.startsWith(base),`Outside deployment prefix: ${url}`);
    const local=url.slice(base.length).split(/[?#]/)[0];
    if(local==='en/'||local==='it/'||local==='')await access(`dist/${local}index.html`);
    else await access(`dist/${local}`);
  }
  assert(html.includes('name="robots" content="noindex,nofollow"'),'WIP preview should not be indexed');
}
for(const style of ['liquid-glass','material-glass','miuix'])for(const type of ['player','appearance'])await access(`public/assets/screenshots/${style}-${type}.png`);
for(const font of ['google-sans-flex','azeret-mono'])await access(`public/assets/fonts/${font}/OFL.txt`);

for(const file of ['rovel-sans/RovelSans-Regular.woff2','rovel-sans/RovelSans-Medium.woff2','rovel-sans/RovelSans-Bold.woff2','character-studies/Puffrelle-Regular.woff2','character-studies/Kindredge-Regular.woff2','rovel-sans/USAGE.txt','character-studies/USAGE.txt'])await access(`public/assets/fonts/${file}`);
console.log('Website checks passed: EN/IT pages, sections, controls, anchors, deployment paths and local assets.');
