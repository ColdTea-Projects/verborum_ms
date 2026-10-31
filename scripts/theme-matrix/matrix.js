const puppeteer = require('puppeteer-core');
const crypto = require('crypto');
const fs = require('fs');
const OUT = process.env.OUT || 'matrix', KC = 'http://localhost:8180';
const WIDTHS = [320, 375, 414, 768, 1024, 1920];
const b64 = b => b.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const authUrl = (path = 'auth') => {
  const v = b64(crypto.randomBytes(32)), c = b64(crypto.createHash('sha256').update(v).digest());
  return `${KC}/realms/verborum/protocol/openid-connect/${path}?client_id=verborum-app&response_type=code&redirect_uri=${encodeURIComponent('http://localhost:8280/')}&scope=${encodeURIComponent('openid profile email offline_access')}&state=s1&code_challenge=${c}&code_challenge_method=S256`;
};
const clickText = (page, text) => page.evaluate(t => { const el = [...document.querySelectorAll('a,button,input[type=submit],label')].find(e => (e.innerText || e.value || '').trim().toLowerCase().includes(t.toLowerCase())); if (el) { el.click(); return true; } return false; }, text);
const issues = [], shots = [];
async function snap(page, name) {
  for (const scheme of ['light', 'dark']) for (const dir of ['ltr', 'rtl']) for (const w of WIDTHS) {
    await page.setViewport({ width: w, height: w >= 1024 ? 900 : 800 });
    await page.emulateMediaFeatures([{ name: 'prefers-color-scheme', value: scheme }]);
    await page.evaluate(d => { document.documentElement.dir = d; document.documentElement.lang = d === 'rtl' ? 'ar' : 'en'; }, dir);
    await sleep(120);
    const r = await page.evaluate(() => {
      const vw = document.documentElement.clientWidth, bad = [];
      for (const e of document.querySelectorAll('body *')) {
        const cs = getComputedStyle(e); if (cs.display === 'none' || cs.visibility === 'hidden' || cs.position === 'fixed') continue;
        const b = e.getBoundingClientRect(); if (!b.width || !b.height) continue;
        if (b.right > vw + 1 || b.left < -1) bad.push((e.id ? '#' + e.id : e.tagName.toLowerCase() + (e.className && typeof e.className === 'string' ? '.' + e.className.split(' ')[0] : '')) + ` [${Math.round(b.left)},${Math.round(b.right)}]`);
      }
      const card = document.querySelector('.card-pf, .pf-c-login__main, #kc-content-wrapper');
      return { scrollW: document.documentElement.scrollWidth, vw, bad: [...new Set(bad)].slice(0, 6), card: card ? Math.round(card.getBoundingClientRect().width) : null };
    });
    const file = `${OUT}/${name}-${scheme}-${dir}-${w}.png`;
    await page.screenshot({ path: file, fullPage: true });
    shots.push(file);
    if (r.scrollW > r.vw || r.bad.length) issues.push({ page: name, scheme, dir, w, scrollW: r.scrollW, bad: r.bad });
  }
}
(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new', defaultViewport: { width: 1000, height: 900 } });
  const timer = setTimeout(() => { console.log('safety timeout'); process.exit(2); }, 560000);
  const probe = 'matrixprobe' + Date.now().toString().slice(-6);
  try {
    const page = await browser.newPage();
    await page.goto(authUrl(), { waitUntil: 'networkidle2' }); await snap(page, '01-login');
    await page.type('#username', 'testuser'); await page.keyboard.press('Enter'); await sleep(2500); await snap(page, '02-password');
    await clickText(page, 'Forgot Password'); await sleep(2500); await snap(page, '03-forgot');
    await page.evaluate(() => { document.querySelector('#username').value = 'testuser'; }); await page.keyboard.press('Enter'); await sleep(4000); await snap(page, '04-forgot-sent');
    await page.goto(authUrl('registrations'), { waitUntil: 'networkidle2' }); await snap(page, '05-register');
    for (const [sel, val] of [['#username', probe], ['#email', probe + '@example.com'], ['#firstName', 'Matrix'], ['#lastName', 'Probe']]) await page.type(sel, val);
    await page.keyboard.press('Enter'); await sleep(4000); await snap(page, '06-verify-email');
    await page.goto(`${KC}/realms/verborum/protocol/openid-connect/auth?client_id=nope&response_type=code&redirect_uri=http%3A%2F%2Flocalhost%3A8280%2F`, { waitUntil: 'networkidle2' }); await snap(page, '07-error');
    await page.goto(authUrl(), { waitUntil: 'networkidle2' }); await page.type('#username', 'testuser'); await page.keyboard.press('Enter'); await sleep(2500);
    await clickText(page, 'Try Another Way'); await sleep(2000); await snap(page, '08-try-another-way');
    await clickText(page, 'email'); await sleep(5000); await snap(page, '09-email-code');
  } finally {
    clearTimeout(timer); await browser.close();
    fs.writeFileSync(`${OUT}/issues.json`, JSON.stringify(issues, null, 1));
    console.log('screenshots:', shots.length, '| issues:', issues.length, '| probe user:', probe);
  }
})().catch(e => { console.log('error', e.message); process.exit(1); });
