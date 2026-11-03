// Renders composition.html frame by frame (30 fps) and pipes PNGs to ffmpeg.
// `node render.js --poster 23` instead writes the frame at t=23 s to poster.png.
const { chromium } = require('playwright');
const { spawn } = require('child_process');
(async () => {
  const FPS = 30, DUR = 24, N = FPS * DUR;
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1920, height: 1080 } });
  p.on('pageerror', e => { console.error('PAGEERR', e.message); process.exit(1); });
  await p.goto('file://' + __dirname + '/composition.html');
  await p.evaluate(() => document.fonts.ready);
  const posterAt = process.argv.indexOf('--poster');
  if (posterAt > 0) {
    await p.evaluate(t => window.render(t), Number(process.argv[posterAt + 1]));
    await p.screenshot({ path: __dirname + '/poster.png', scale: 'css' });
    await b.close();
    return;
  }
  const ff = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(FPS), '-i', '-',
    '-c:v', 'libx264', '-preset', 'slow', '-crf', '16', '-pix_fmt', 'yuv420p', 'video_silent.mp4'], { stdio: ['pipe', 'inherit', 'inherit'] });
  for (let f = 0; f < N; f++) {
    await p.evaluate(t => window.render(t), f / FPS);
    const buf = await p.screenshot({ type: 'png', scale: 'css' });
    if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
    if (f % 90 === 0) console.log('frame', f);
  }
  ff.stdin.end();
  await new Promise(r => ff.on('close', r));
  await b.close();
  console.log('done');
})();
