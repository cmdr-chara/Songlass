import { chromium } from 'playwright-core';
import assert from 'node:assert/strict';

// Browser validation against the production preview.
// Use the production deployment root, including /Songlass/ for the Pages build.
const url = new URL(process.env.SONGLASS_REVIEW_URL ?? 'http://127.0.0.1:4173/');
url.pathname = url.pathname.replace(/\/?$/, '/');
url.search = '';
url.hash = '';
const browser = await chromium.launch({
  executablePath: process.env.CHROMIUM_PATH ?? chromium.executablePath(),
  args: ['--no-sandbox'],
});
const errors = [];
const mediaRequests = [];
const editions = [['liquid_glass', 'liquid-glass'], ['material_design', 'material-glass'], ['miuix', 'miuix']];
function watch(page) {
  page.on('pageerror', error => errors.push(error.message));
  page.on('response', response => { if (response.status() >= 400) errors.push(`${response.status()} ${response.url()}`); });
  page.on('request', request => { if (request.resourceType() === 'media') mediaRequests.push(request.url()); });
}
async function selectEdition(page, key, file, picker = 'intro') {
  const prefix = picker === 'intro' ? 'style' : `${picker}_style`;
  await page.locator(`#${prefix}_${key}`).check();
  await page.waitForFunction(style => document.documentElement.dataset.style === style && document.documentElement.dataset.styleReady === 'true', key);
  assert.deepEqual(await page.locator('[data-style-choice]:checked').evaluateAll(inputs => inputs.map(input => input.value)), [key, key, key], 'all three style selectors commit the same edition');
  assert.equal(await page.locator('.edition-player').getAttribute('data-edition'), key);
  assert.equal(await page.locator('.hero-gallery').getAttribute('data-edition'), key);
  assert.equal(await page.locator('.hero-gallery img').count(), 3, 'all three native captures remain in the gallery');
  const centre = page.locator('.hero-gallery img[data-position="center"]');
  assert.equal(await centre.count(), 1);
  assert.match(await centre.getAttribute('src'), new RegExp(`${file}-player\\.webp$`));
  assert((await centre.getAttribute('src')).startsWith(`${url.pathname}assets/`), 'gallery assets respect the deployment prefix');
  const references = await page.locator('.screenshot-reference').evaluateAll(links => links.map(link => link.getAttribute('href')));
  assert.deepEqual(references, [`${url.pathname}assets/screenshots/${file}-player.png`, `${url.pathname}assets/screenshots/${file}-appearance.png`], 'both original captures follow the selected edition');
}
async function noOverflow(page) {
  assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), 'document overflow');
  assert(await page.locator('h1').evaluate(element => element.scrollWidth <= element.clientWidth + 2), 'hero text must fit its own column');
  assert(await page.locator('.edition-player').evaluate(element => element.scrollWidth <= element.clientWidth + 1), 'player content must fit its own surface');
}
async function waitForAudio(page, selector, playing) {
  await page.waitForFunction(({ selector, playing }) => {
    const demo = document.querySelector(selector);
    const active = [...document.querySelectorAll('.edition-player,.listening-lab')].filter(element => element.dataset.playing === 'true').length;
    const running = window.__songlassReviewAudio.filter(context => context.state === 'running').length;
    return demo && (demo.dataset.playing === 'true') === playing && active === (playing ? 1 : 0) && running === (playing ? 1 : 0);
  }, { selector, playing });
}
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, colorScheme: 'light', locale: 'en-US' });
  watch(page);
  // Observe real contexts without substituting a mock audio engine. Loading
  // the page and editing EQ must never create a context or start sound.
  await page.addInitScript(() => {
    const contexts = [];
    window.__songlassReviewAudio = contexts;
    const NativeContext = window.AudioContext ?? window.webkitAudioContext;
    if (!NativeContext) return;
    const ObservedContext = new Proxy(NativeContext, {
      construct(target, args, newTarget) {
        const context = Reflect.construct(target, args, newTarget);
        contexts.push(context);
        return context;
      },
    });
    if (window.AudioContext) window.AudioContext = ObservedContext;
    else window.webkitAudioContext = ObservedContext;
  });
  await page.goto(url.href, { waitUntil: 'networkidle' });
  await page.waitForFunction(() => document.documentElement.dataset.styleReady === 'true');
  assert.equal(await page.locator('h1').count(), 1);
  assert.equal(await page.locator('main > section').count(), 8);
  assert.equal(await page.locator('.hero-actions a.action-primary').getAttribute('href'), 'https://github.com/cmdr-chara/Songlass/releases/latest');
  assert.equal(await page.locator('html').getAttribute('data-scroll-engine'), 'lenis');
  assert.equal(await page.locator('.edition-player').count(), 1);
  const landscape = await page.locator('.hero-art-plane img').getAttribute('src');
  assert(landscape.startsWith(`${url.pathname}assets/footer/`) && landscape.endsWith('.webp'), 'the hero uses a local landscape under the deployment prefix');
  assert.equal(await page.evaluate(() => window.__songlassReviewAudio.length), 0, 'no audio context on page load');

  // Check both languages and all styles at desktop, tablet, and phone widths.
  for (const locale of ['en', 'it']) {
    if (locale === 'it') {
      await page.locator('.language-toggle').click();
      assert.equal(new URL(page.url()).pathname, `${url.pathname}it/`);
      await page.reload({ waitUntil: 'networkidle' });
    }
    assert.equal(await page.locator('html').getAttribute('lang'), locale);
    for (const width of [1440, 768, 390, 320]) {
      await page.setViewportSize({ width, height: 900 });
      for (const [key, file] of editions) { await selectEdition(page, key, file); await noOverflow(page); }
    }
  }
  // Functional controls below are addressed by their English accessible names.
  await page.locator('.language-toggle').click();
  await page.waitForFunction(() => document.documentElement.lang === 'en');
  await page.locator('.hero-caption').click();
  assert(await page.locator('#screen-preview').evaluate(element => element.open));
  await page.keyboard.press('Escape');
  assert(await page.locator('.hero-caption').evaluate(element => element === document.activeElement));
  assert(!(await page.locator('#screen-preview').evaluate(element => element.open)));
  const appearanceReference = page.locator('.screenshot-reference').nth(1);
  await appearanceReference.click();
  assert(await page.locator('#screen-preview').evaluate(element => element.open));
  assert.match(await page.locator('#screen-preview img').getAttribute('src'), /miuix-appearance\.png$/);
  await page.keyboard.press('Escape');
  assert(await appearanceReference.evaluate(element => element === document.activeElement));

  await page.locator('.menu-toggle').click();
  assert.equal(await page.locator('.menu-toggle').getAttribute('aria-expanded'), 'true');
  await page.keyboard.press('Escape');
  assert.equal(await page.locator('.menu-toggle').getAttribute('aria-expanded'), 'false');
  assert(await page.locator('.menu-toggle').evaluate(element => element === document.activeElement));
  await page.locator('.menu-toggle').click();
  await page.locator('#mobile-navigation a[href="#music_library"]').click();
  assert.equal(await page.locator('.menu-toggle').getAttribute('aria-expanded'), 'false');
  assert.equal(new URL(page.url()).hash, '#music_library');
  await page.locator('#library-tab-0').focus();
  await page.keyboard.press('End');
  assert.equal(await page.locator('#library-tab-4').getAttribute('aria-selected'), 'true');
  assert(await page.locator('#library-tab-4').evaluate(element => element === document.activeElement));
  await page.keyboard.press('ArrowRight');
  assert.equal(await page.locator('#library-tab-0').getAttribute('aria-selected'), 'true');
  await page.keyboard.press('ArrowDown');
  assert.equal(await page.locator('#library-tab-1').getAttribute('aria-selected'), 'true');
  assert.equal(await page.locator('#library-view').getAttribute('aria-labelledby'), 'library-tab-1');
  assert.equal(await page.locator('.library-index [aria-selected="true"]').count(), 1);

  // The motion and opacity controls share a visual class, so select them by
  // their distinct accessible labels rather than a now-ambiguous CSS selector.
  const reduceMotion = page.getByRole('checkbox', { name: 'Reduce website motion', exact: true });
  const opaqueSurfaces = page.getByRole('checkbox', { name: 'Opaque website surfaces', exact: true });
  await page.locator('input[name="site-theme"][value="dark"]').check();
  await reduceMotion.check();
  await opaqueSurfaces.check();
  await page.reload({ waitUntil: 'networkidle' });
  await page.waitForFunction(() => document.documentElement.dataset.theme === 'dark' && document.documentElement.dataset.motionPref === 'reduced' && document.documentElement.dataset.transparency === 'reduced');
  assert(await page.locator('input[name="site-theme"][value="dark"]').isChecked());
  assert(await reduceMotion.isChecked());
  assert(await opaqueSurfaces.isChecked());
  assert.equal(await page.evaluate(() => window.getComputedStyle(document.documentElement).colorScheme), 'dark');
  await selectEdition(page, 'liquid_glass', 'liquid-glass', 'showcase');
  assert.equal(await page.locator('.edition-player__utilities').evaluate(element => window.getComputedStyle(element).getPropertyValue('backdrop-filter')), 'none', 'opaque surfaces remove the Liquid utility blur');
  assert.equal(await page.locator('.hero-gallery').evaluate(element => window.getComputedStyle(element).getPropertyValue('--gallery-progress').trim()), '0', 'reduced motion freezes scroll travel');
  await opaqueSurfaces.uncheck();
  await page.waitForFunction(() => document.documentElement.dataset.transparency === 'full');
  await opaqueSurfaces.check();
  await page.locator('input[name="site-theme"][value="light"]').check();
  // OS reduced motion still wins when the optional website switch is off.
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await reduceMotion.uncheck();
  await page.waitForFunction(() => document.documentElement.dataset.motionPref === 'reduced');
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.waitForFunction(() => document.documentElement.dataset.motionPref === 'full');
  await reduceMotion.check();

  // The native-inspired showcase has real, independent controls. Seeking and
  // changing its favorite/queue state must not create an audio context.
  await page.setViewportSize({ width: 1440, height: 1000 });
  const player = page.locator('.edition-player');
  const seek = player.locator('input[type="range"]');
  const queueButton = player.getByRole('button', { name: 'Queue', exact: true });
  const favorite = player.getByRole('button', { name: 'Favorite', exact: true });
  await player.scrollIntoViewIfNeeded();
  assert.equal(await page.evaluate(() => window.__songlassReviewAudio.length), 0);
  await favorite.click();
  await queueButton.click();
  assert.equal(await favorite.getAttribute('aria-pressed'), 'true');
  assert.equal(await queueButton.getAttribute('aria-expanded'), 'true');
  assert.equal(await player.locator('.edition-player__queue li').count(), 1);
  await queueButton.press('Escape');
  assert.equal(await queueButton.getAttribute('aria-expanded'), 'false');
  assert(await queueButton.evaluate(element => element === document.activeElement));
  await queueButton.click();
  await seek.press('End');
  assert.equal(Number(await seek.inputValue()), 12);
  await player.getByRole('button', { name: 'Back 3 seconds', exact: true }).click();
  assert.equal(Number(await seek.inputValue()), 9);
  await player.getByRole('button', { name: 'Forward 3 seconds', exact: true }).click();
  assert.equal(Number(await seek.inputValue()), 12);
  await seek.press('Home');
  await player.getByRole('button', { name: 'Forward 3 seconds', exact: true }).click();
  assert.equal(Number(await seek.inputValue()), 3);
  const controlGeometry = [];
  for (const [key, file] of editions) {
    await selectEdition(page, key, file, 'showcase');
    assert.equal(Number(await seek.inputValue()), 3, 'paused position survives an edition change');
    assert.equal(await favorite.getAttribute('aria-pressed'), 'true');
    assert.equal(await queueButton.getAttribute('aria-expanded'), 'true');
    controlGeometry.push(await player.evaluate(element => {
      const play = window.getComputedStyle(element.querySelector('.edition-player__play'));
      const rail = window.getComputedStyle(element.querySelector('.edition-player__remaining'));
      const utilities = window.getComputedStyle(element.querySelector('.edition-player__utilities'));
      return [play.width, play.borderTopLeftRadius, rail.height, utilities.gap].join('|');
    }));
  }
  assert.equal(new Set(controlGeometry).size, 3, 'edition changes alter control geometry, not just color');
  assert.equal(await page.evaluate(() => window.__songlassReviewAudio.length), 0, 'non-play controls never start audio');

  await player.locator('.edition-player__play').click();
  await waitForAudio(page, '.edition-player', true);
  await page.waitForFunction(() => Number(document.querySelector('.edition-player input[type="range"]').value) > 3.15);
  assert.equal(await page.evaluate(() => window.__songlassReviewAudio.length), 1);
  const readClock = () => page.evaluate(() => {
    const input = document.querySelector('.edition-player input[type="range"]');
    const context = window.__songlassReviewAudio.find(candidate => candidate.state === 'running');
    return { position: Number(input.value), duration: Number(input.max), clock: context.currentTime };
  });
  for (const [key, file] of editions) {
    const before = await readClock();
    await selectEdition(page, key, file, 'showcase');
    await waitForAudio(page, '.edition-player', true);
    const after = await readClock();
    const expected = (before.position + after.clock - before.clock) % after.duration;
    const distance = Math.abs(after.position - expected);
    assert(Math.min(distance, after.duration - distance) < .45, 'edition switching preserves the running audio clock');
    assert.equal(await page.evaluate(() => window.__songlassReviewAudio.length), 1, 'edition switching reuses the existing audio context');
    assert.equal(await favorite.getAttribute('aria-pressed'), 'true');
    assert.equal(await queueButton.getAttribute('aria-expanded'), 'true');
  }
  await player.locator('.edition-player__play').click();
  await waitForAudio(page, '.edition-player', false);
  const pausedAt = Number(await seek.inputValue());
  // Observe a full clock-update interval to catch a counter that keeps running.
  await page.waitForTimeout(180);
  assert.equal(Number(await seek.inputValue()), pausedAt);
  await queueButton.press('Escape');

  // Material's decorative wave follows actual playback and the motion setting.
  await reduceMotion.uncheck();
  await selectEdition(page, 'material_design', 'material-glass', 'showcase');
  await player.locator('.edition-player__play').click();
  await waitForAudio(page, '.edition-player', true);
  assert(await player.locator('.edition-player__wave').isVisible());
  assert.equal(await player.locator('.edition-player__wave').evaluate(element => window.getComputedStyle(element).animationPlayState), 'running');
  await seek.press('Home');
  await page.waitForFunction(() => {
    const value = Number(document.querySelector('.edition-player input[type="range"]').value);
    return value > .1 && value < 2;
  });
  await player.locator('.edition-player__play').click();
  await waitForAudio(page, '.edition-player', false);
  assert(!(await player.locator('.edition-player__wave').isVisible()));
  await reduceMotion.check();

  const lab = page.locator('.listening-lab');
  await lab.scrollIntoViewIfNeeded();
  const contextsBeforeEq = await page.evaluate(() => window.__songlassReviewAudio.length);
  await page.locator('.listening-lab__presets button').nth(1).click();
  assert.deepEqual(await page.locator('.listening-lab__band input').evaluateAll(inputs => inputs.map(input => Number(input.value))), [5, -2, -3]);
  assert.equal(await page.evaluate(() => window.__songlassReviewAudio.length), contextsBeforeEq, 'EQ changes do not create audio contexts');
  await page.locator('#listening-lab-band-0').focus();
  await page.keyboard.press('End');
  assert.equal(await page.locator('#listening-lab-band-0').inputValue(), '9');
  await page.locator('.listening-lab__reset').click();
  assert.deepEqual(await page.locator('.listening-lab__band input').evaluateAll(inputs => inputs.map(input => Number(input.value))), [0, 0, 0]);
  await page.locator('.listening-lab__play').click();
  await waitForAudio(page, '.listening-lab', true);
  assert.equal(await player.getAttribute('data-playing'), 'false');
  assert.equal(await page.evaluate(() => window.__songlassReviewAudio.filter(context => context.state === 'running').length), 1, 'only the visible demo owns playback');
  const meterBefore = await page.locator('.listening-lab__spectrum span').evaluateAll(bars => bars.map(bar => bar.style.getPropertyValue('--lab-level')));
  // A deliberate observation window verifies that the manual reduced-motion
  // setting keeps the meter still while audio continues to play.
  await page.waitForTimeout(180);
  assert.deepEqual(await page.locator('.listening-lab__spectrum span').evaluateAll(bars => bars.map(bar => bar.style.getPropertyValue('--lab-level'))), meterBefore);
  await page.locator('.listening-lab__play').click();
  await waitForAudio(page, '.listening-lab', false);
  await page.locator('.listening-lab__play').click();
  await waitForAudio(page, '.listening-lab', true);
  await page.locator('.hero-copy').scrollIntoViewIfNeeded();
  await waitForAudio(page, '.listening-lab', false);
  await lab.scrollIntoViewIfNeeded();
  await page.locator('.listening-lab__play').click();
  await waitForAudio(page, '.listening-lab', true);
  // Exercise the registered pagehide cleanup without manufacturing audio state.
  await page.evaluate(() => window.dispatchEvent(new window.PageTransitionEvent('pagehide')));
  await waitForAudio(page, '.listening-lab', false);

  const staticPage = await browser.newPage({ javaScriptEnabled: false, viewport: { width: 390, height: 844 } });
  watch(staticPage);
  for (const locale of ['en', 'it']) {
    await staticPage.goto(new URL(`${locale}/`, url).href, { waitUntil: 'networkidle' });
    assert.equal(await staticPage.locator('html').getAttribute('lang'), locale);
    assert.equal(await staticPage.locator('main > section').count(), 8, 'all content is pre-rendered without JavaScript');
    assert.equal(await staticPage.locator('.hero-gallery img').count(), 3);
    assert((await staticPage.locator('.hero-caption').getAttribute('href')).startsWith(`${url.pathname}assets/`));
    assert.equal(await staticPage.locator('.screenshot-reference').count(), 2);
    assert(await staticPage.locator('.screenshot-reference').evaluateAll((links, prefix) => links.every(link => link.getAttribute('href').startsWith(`${prefix}assets/`)), url.pathname), 'original captures remain direct links without JavaScript');
    assert.equal(await staticPage.locator('.edition-player').count(), 1);
    assert(await staticPage.locator('.edition-player__noscript').isVisible(), 'the static player explains how to enable listening');
    assert(!(await staticPage.locator('.edition-player__play').isVisible()), 'inert play controls are hidden without JavaScript');
    await staticPage.locator('#network_answer summary').click();
    assert(await staticPage.locator('#network_answer').evaluate(element => element.open), 'native FAQ disclosures work without JavaScript');
    assert.equal(await staticPage.locator('.hero-actions a.action-primary').getAttribute('href'), 'https://github.com/cmdr-chara/Songlass/releases/latest');
  }
  await staticPage.close();
  await page.close();
  assert.deepEqual(errors, []);
  assert.deepEqual(mediaRequests, [], 'the sound study does not request an external audio file');
  console.log('Browser smoke passed: responsive EN/IT galleries, distinct edition controls and preserved state, synchronized styles, keyboard navigation, preview focus, saved theme/motion/opacity, opt-in audio and EQ, smooth scroll with native fallbacks, deployment prefixes and no-JS content.');
} finally {
  await browser.close();
}
