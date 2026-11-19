// Renders the navigation walkthrough frame by frame (30 fps) and pipes PNGs to ffmpeg.
//   node render.js                 -> video_silent.mp4 in the current directory
//   node render.js --still 12.5 x.png  -> one frame, for checking a moment
// It opens ../store_assets/store.html (the app screens, device frame and icons shared with
// the store graphics) and injects nav.js, which defines the timeline and window.render(t).
const { chromium } = require('playwright');
const { spawn } = require('child_process');
const path = require('path');
(async () => {
  const FPS = 30;
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1920, height: 1080 } });
  p.on('pageerror', e => { console.error('PAGEERR', e.message); process.exit(1); });
  await p.goto('file://' + path.join(__dirname, '..', 'store_assets', 'store.html'));
  await p.addScriptTag({ path: path.join(__dirname, 'nav.js') });
  await p.evaluate(() => document.fonts.ready);
  const DUR = await p.evaluate(() => window.DURATION);
  const stillAt = process.argv.indexOf('--still');
  if (stillAt > 0) {
    await p.evaluate(t => window.render(t), Number(process.argv[stillAt + 1]));
    await p.waitForTimeout(100);
    await p.screenshot({ path: process.argv[stillAt + 2] || 'still.png' });
    await b.close();
    return;
  }
  const N = Math.round(FPS * DUR);
  const ff = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(FPS), '-i', '-',
    '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', '-pix_fmt', 'yuv420p', 'video_silent.mp4'], { stdio: ['pipe', 'inherit', 'inherit'] });
  for (let f = 0; f < N; f++) {
    await p.evaluate(t => window.render(t), f / FPS);
    const buf = await p.screenshot({ type: 'png' });
    if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
    if (f % 300 === 0) console.log('frame', f, '/', N);
  }
  ff.stdin.end();
  await new Promise(r => ff.on('close', r));
  await b.close();
  console.log('done', DUR.toFixed(1) + 's');
})();
