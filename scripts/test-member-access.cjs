'use strict';
// This checks both dashboards against a local test server using temporary records.
const assert = require('node:assert/strict');
const { JSDOM, VirtualConsole } = require('jsdom');
const base = process.env.FITTRACK_API_URL || 'http://127.0.0.1:8094';
const username = process.env.FITTRACK_ADMIN_USER;
const password = process.env.FITTRACK_ADMIN_PASSWORD;
if (!['127.0.0.1', 'localhost', '::1', '[::1]'].includes(new URL(base).hostname)) {
  throw new Error('These checks are only for a local memory-mode test server.');
}
if (!username || !password) throw new Error('Test admin credentials are required.');
const id = 910001;
const otherId = 910002;
const memberPassword = 'a-member-ui-test-password';
const errors = [];
let cookie = '';
let adminPage;
let memberPage;
let seeded = false;

async function request(path, method = 'GET', body) {
  const response = await fetch(new URL(path, base), {
    method, redirect: 'manual', headers: {
      Cookie: cookie, 'X-FitTrack-Request': 'true', 'Content-Type': 'application/json'
    }, body: body === undefined ? undefined : JSON.stringify(body)
  });
  const saved = response.headers.get('set-cookie');
  if (saved) cookie = saved.includes('Max-Age=0') ? '' : saved.split(';', 1)[0];
  return response;
}
async function waitFor(check) {
  for (let attempt = 0; attempt < 500; attempt++) {
    if (check()) return;
    await new Promise(resolve => setTimeout(resolve, 20));
  }
  throw new Error('The page did not reach the expected state.');
}
async function dashboard() {
  const output = new VirtualConsole();
  output.on('jsdomError', error => {
    if (!error.message.includes('Not implemented: navigation')) errors.push(error.message);
  });
  const dom = new JSDOM(await (await request('/')).text(), {
    url: base, runScripts: 'outside-only', virtualConsole: output
  });
  dom.window.HTMLDialogElement.prototype.showModal = function () { this.open = true; };
  dom.window.HTMLDialogElement.prototype.close = function () {
    this.open = false; this.dispatchEvent(new dom.window.Event('close'));
  };
  dom.window.fetch = (path, options = {}) => request(path, options.method || 'GET',
    options.body ? JSON.parse(options.body) : undefined);
  dom.window.eval(await (await request('/app.js')).text());
  await waitFor(() => dom.window.document.getElementById('stat-members').textContent !== '—'
    && !dom.window.document.getElementById('refresh').disabled);
  assert.equal(dom.window.document.getElementById('error').hidden, true);
  return dom;
}

(async () => {
  assert.equal((await request('/auth/login', 'POST', { username, password })).status, 200);
  // Existing records with these test IDs are left alone.
  for (const memberId of [id, otherId]) {
    assert.equal((await request(`/members/${memberId}`)).status, 404, 'Test member ID already exists.');
  }
  seeded = true;
  for (const memberId of [id, otherId]) {
    assert.equal((await request('/members', 'POST', {
      id: memberId, name: `UI Member ${memberId}`, email: `ui${memberId}@example.com`, age: 25
    })).status, 201);
    assert.equal((await request('/workouts', 'POST', {
      id: memberId, memberId, activityType: 'UI Walking', durationMinutes: 30, date: '2026-01-01'
    })).status, 201);
    assert.equal((await request('/challenges', 'POST', {
      id: memberId, memberId, name: `UI Goal ${memberId}`, type: 'MINUTES', target: 60,
      startDate: '2026-01-01', endDate: '2026-01-31'
    })).status, 201);
  }
  adminPage = await dashboard();
  const screen = adminPage.window.document;
  assert.equal(screen.querySelector('[data-add="members"]').hidden, false);
  const memberRow = [...screen.querySelectorAll('#members-list tr')].find(row => row.textContent.includes(`UI Member ${id}`));
  [...memberRow.querySelectorAll('button')].find(button => button.textContent === 'Set up login').click();
  screen.querySelector('[name="username"]').value = 'ui-member-910001';
  screen.querySelector('[name="password"]').value = memberPassword;
  screen.getElementById('record-form').dispatchEvent(new adminPage.window.Event('submit', { bubbles: true, cancelable: true }));
  await waitFor(() => !screen.getElementById('record-dialog').open && !screen.getElementById('refresh').disabled);
  assert.equal(screen.getElementById('form-error').hidden, true);
  assert.equal(screen.querySelector('[name="password"]').value, '');
  assert.ok(screen.getElementById('members-list').textContent.includes('Reset login'));

  assert.equal((await request('/auth/login', 'POST', { username: 'ui-member-910001', password: memberPassword })).status, 200);
  // Logging in replaces the old browser session. A separate admin login is needed for cleanup.
  memberPage = await dashboard();
  const own = memberPage.window.document;
  assert.equal(own.getElementById('members-title').textContent, 'My profile');
  assert.equal(own.getElementById('stat-members').textContent, '1');
  assert.ok([...own.querySelectorAll('[data-add]')].every(button => button.hidden));
  assert.equal(own.querySelectorAll('#members-list button, #workouts-list button').length, 0);
  assert.equal(own.querySelectorAll('#challenges-list button').length, 1);
  assert.ok(!own.getElementById('members-list').textContent.includes(String(otherId)));
  assert.ok(!own.getElementById('workouts-list').textContent.includes(`UI Member ${otherId}`));
  assert.ok(!own.getElementById('challenges-list').textContent.includes(`UI Goal ${otherId}`));
  own.querySelector('#challenges-list button').click();
  own.getElementById('confirm-delete').click();
  await waitFor(() => !own.getElementById('delete-dialog').open && !own.getElementById('refresh').disabled);
  assert.equal((await request(`/challenges/${id}`)).status, 404);
  assert.deepEqual(errors, []);
  console.log('PASS: admin login setup, cleared password, own profile only, hidden admin controls and member goal deletion.');
})().catch(error => { console.error(error); process.exitCode = 1; }).finally(async () => {
  adminPage?.window.close(); memberPage?.window.close();
  if (cookie) await request('/auth/logout', 'POST');
  cookie = '';
  const login = await request('/auth/login', 'POST', { username, password });
  if (seeded && login.status === 200) {
    for (const memberId of [id, otherId]) {
      for (const resource of ['challenges', 'workouts', 'members']) {
        await request(`/${resource}/${memberId}`, 'DELETE');
      }
    }
  }
  if (cookie) await request('/auth/logout', 'POST');
});
