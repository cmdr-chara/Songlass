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
  const staticPage=await browser.newPage({javaScriptEnabled:false});
  await staticPage.goto(new URL('it/',url).href);
  assert.equal(await staticPage.locator('html').getAttribute('lang'),'it');
  assert.equal(await staticPage.locator('main>section').count(),8);
  assert.deepEqual(errors,[]);
  console.log('Browser smoke passed: assets/WebGL, styles, prefixed language reload, preview focus, compact/reduced motion and no-JS Italian.');
}finally{await browser.close();}
