import {chromium} from 'playwright-core';
import assert from 'node:assert/strict';
const url=new URL(process.env.SONGLASS_REVIEW_URL ?? 'http://127.0.0.1:4173/');
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH ?? chromium.executablePath(),args:['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
const errors=[];
try{
  const page=await browser.newPage({viewport:{width:1440,height:900},colorScheme:'dark'});
  page.on('pageerror',error=>errors.push(error.message));
  page.on('response',response=>{if(response.status()>=400)errors.push(`${response.status()} ${response.url()}`);});
  await page.goto(url.href,{waitUntil:'networkidle'});
  await page.waitForFunction(()=>document.documentElement.dataset.webglReady==='true');
  for(const style of ['material_design','miuix','liquid_glass']){
    await page.locator(`#style_${style}`).click();
    await page.waitForFunction(s=>document.documentElement.dataset.style===s,style);
    assert.equal(await page.locator('[data-style-choice]:checked').count(),3);
  }
  await page.locator('.language-toggle').click();
  assert.equal(new URL(page.url()).pathname,url.pathname+'it/');
  await page.reload({waitUntil:'networkidle'});
  assert.equal(await page.locator('html').getAttribute('lang'),'it');
  assert(await page.locator('.hero-fallback').getAttribute('src').then(src=>src.startsWith(url.pathname+'assets/')));
  await page.locator('.stage-open').click();
  assert(await page.locator('#screen-preview').evaluate(el=>el.open));
  await page.keyboard.press('Escape');
  assert(await page.locator('.stage-open').evaluate(el=>el===document.activeElement));
  await page.emulateMedia({reducedMotion:'reduce'});
  await page.setViewportSize({width:390,height:844});
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));
  const shortPage=await browser.newPage({viewport:{width:1488,height:758},colorScheme:'dark'});
  await shortPage.goto(url.href,{waitUntil:'networkidle'});
  await shortPage.waitForFunction(()=>document.querySelector('[data-story-enhanced=true]'));
  for(const height of [758,700]){
    await shortPage.setViewportSize({width:1488,height});
    for(const style of ['liquid_glass','material_design','miuix']){
      await shortPage.evaluate(style=>document.getElementById('style_'+style).click(),style);
      await shortPage.waitForFunction(style=>document.documentElement.dataset.style===style,style);
      assert(await shortPage.locator('[data-story-enhanced=true]').count(),'short desktop keeps the animated story');
      await shortPage.evaluate(()=>{document.activeElement?.blur();window.scrollTo({top:document.documentElement.scrollHeight,behavior:'instant'});});
      await shortPage.waitForFunction(()=>document.querySelector('.site-footer').getBoundingClientRect().bottom<=window.innerHeight+1);
      assert(await shortPage.locator('.site-footer .brand-link').evaluate(el=>el.getBoundingClientRect().top>=0),'footer brand remains visible at page bottom');
      assert(await shortPage.locator('.footer-selector-material').evaluate(el=>el.getBoundingClientRect().bottom<=window.innerHeight),'footer selector remains visible');
      await shortPage.evaluate(()=>window.scrollTo({top:0,behavior:'instant'}));
    }
  }
  await shortPage.close();
  const staticPage=await browser.newPage({javaScriptEnabled:false});
  await staticPage.goto(new URL('it/',url).href);
  assert.equal(await staticPage.locator('html').getAttribute('lang'),'it');
  assert.equal(await staticPage.locator('main>section').count(),8);
  const fallbackPage=await browser.newPage({viewport:{width:1440,height:900},colorScheme:'dark'});
  await fallbackPage.addInitScript(()=>{
    const getContext=window.HTMLCanvasElement.prototype.getContext;
    window.HTMLCanvasElement.prototype.getContext=function(type,...args){
      if(type==='webgl'||type==='webgl2'||type==='experimental-webgl')return null;
      return getContext.call(this,type,...args);
    };
  });
  await fallbackPage.goto(url.href,{waitUntil:'networkidle'});
  await fallbackPage.waitForFunction(()=>document.documentElement.dataset.webglReady==='false');
  const visiblePlayers=()=>fallbackPage.locator('.hero-product img').evaluateAll(images=>images.filter(image=>image.checkVisibility()).length);
  assert.equal(await visiblePlayers(),3,'WebGL failure preserves the three-player desktop composition');
  await fallbackPage.locator('#style_material_design').click();
  await fallbackPage.waitForFunction(()=>document.documentElement.dataset.style==='material_design');
  assert.match(await fallbackPage.locator('.hero-fallback').getAttribute('src'),/material-glass-player/);
  assert.equal(await visiblePlayers(),3);
  await fallbackPage.setViewportSize({width:1440,height:700});
  assert.equal(await visiblePlayers(),3,'short desktop windows retain the complete composition');
  await fallbackPage.emulateMedia({reducedMotion:'reduce'});
  assert.equal(await visiblePlayers(),3);
  await fallbackPage.setViewportSize({width:390,height:844});
  assert.equal(await visiblePlayers(),1,'compact screens keep one complete readable capture');
  assert(await fallbackPage.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await fallbackPage.close();
  assert.deepEqual(errors,[]);
  console.log('Browser smoke passed: assets/WebGL, styles, prefixed language reload, preview focus, compact/reduced motion, no-JS Italian and desktop/compact WebGL fallback.');
}finally{await browser.close();}
