'use strict';
// These optional checks use a local memory-mode server with test-only admin credentials.
const assert = require('node:assert/strict');
const { JSDOM, VirtualConsole } = require('jsdom');
const base = process.env.FITTRACK_API_URL || 'http://127.0.0.1:8094';
const username = process.env.FITTRACK_ADMIN_USER;
const password = process.env.FITTRACK_ADMIN_PASSWORD;
const address = new URL(base);
if (!['127.0.0.1', 'localhost', '::1', '[::1]'].includes(address.hostname)) {
  throw new Error('These checks are only for a local test server.');
}
if (!username || !password) throw new Error('Test admin credentials are required.');

// This small cookie jar lets the Node test behave like the browser's cookie handling.
let cookie = '';
let logoutFinished = false;
const errors = [];
async function request(path, options = {}) {
  const response = await fetch(new URL(path, base), {
    ...options,
    redirect: 'manual',
    headers: { ...options.headers, ...(cookie ? { Cookie: cookie } : {}) }
  });
  const header = response.headers.get('set-cookie');
  if (header) cookie = header.includes('Max-Age=0') ? '' : header.split(';', 1)[0];
  if (path === '/auth/logout') logoutFinished = response.status === 204;
  return response;
}
async function waitFor(check) {
  for (let attempt = 0; attempt < 200; attempt++) {
    if (check()) return;
    await new Promise(resolve => setTimeout(resolve, 20));
  }
  throw new Error('The login page did not reach the expected state.');
}
function page(html) {
  const output = new VirtualConsole();
  output.on('jsdomError', error => {
    // jsdom cannot navigate to another page; the real HTTP results are checked separately.
    if (!error.message.includes('Not implemented: navigation')) errors.push(error.message);
  });
  const dom = new JSDOM(html, { url: base, runScripts: 'outside-only', virtualConsole: output });
  dom.window.fetch = request;
  return dom;
}

(async () => {
  const login = page(await (await request('/login')).text());
  let dashboard;
  try {
    login.window.eval(await (await request('/login.js')).text());
    const document = login.window.document;
    document.querySelector('[name="username"]').value = username;
    document.querySelector('[name="password"]').value = password;
    document.getElementById('login-form').dispatchEvent(
      new login.window.Event('submit', { bubbles: true, cancelable: true }));
    await waitFor(() => cookie && !document.getElementById('sign-in').disabled);
    assert.equal(document.getElementById('login-error').hidden, true);
    assert.equal(document.querySelector('[name="password"]').value, '');
    const oldCookie = cookie;
    assert.equal((await request('/members')).status, 200);

    dashboard = page(await (await request('/')).text());
    dashboard.window.eval(await (await request('/app.js')).text());
    const screen = dashboard.window.document;
    await waitFor(() => !screen.getElementById('logout').hidden
      && !screen.getElementById('refresh').disabled
      && screen.getElementById('stat-members').textContent !== '—');
    screen.getElementById('logout').click();
    await waitFor(() => logoutFinished && cookie === '');
    assert.equal((await request('/members')).status, 401);
    const reused = await fetch(new URL('/members', base), { headers: { Cookie: oldCookie } });
    assert.equal(reused.status, 401);
    assert.deepEqual(errors, []);
    console.log('PASS: login form, cleared password, visible logout button, cookie removal and rejection of the old session.');
  } finally {
    if (cookie) await request('/auth/logout', { method: 'POST', headers: { 'X-FitTrack-Request': 'true' } });
    login.window.close();
    dashboard?.window.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
