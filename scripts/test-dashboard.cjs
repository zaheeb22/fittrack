'use strict';
// These optional webpage checks use a separate, empty memory-mode server.
// This checks webpage actions and content. It does not check the visual layout.
const { JSDOM } = require('jsdom');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.FITTRACK_API_URL || 'http://127.0.0.1:8092';
const root = path.join(__dirname, '..');

(async () => {
  // This stops the checks if the server already contains records.
  for (const resource of ['members', 'workouts', 'challenges']) {
    const response = await fetch(`${base}/${resource}`);
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), [], 'Use a separate empty memory-mode server.');
  }
  const html = await (await fetch(base + '/')).text();
  const script = await (await fetch(base + '/app.js')).text();
  assert.equal(html, fs.readFileSync(path.join(root, 'src/main/resources/web/index.html'), 'utf8'), 'Rebuild the JAR after editing the HTML.');
  assert.equal(script, fs.readFileSync(path.join(root, 'src/main/resources/web/app.js'), 'utf8'), 'Rebuild the JAR after editing JavaScript.');
  const page = new JSDOM(html, {
    url: base, runScripts: 'outside-only', pretendToBeVisual: true
  });
  const { window } = page;
  const document = window.document;
  // These functions let the test tool open and close dialogs.
  window.HTMLDialogElement.prototype.showModal = function () { this.open = true; };
  window.HTMLDialogElement.prototype.close = function () { this.open = false; };
  window.fetch = (url, options) => fetch(new URL(url, base), options);
  const get = (id) => document.getElementById(id);
  async function waitFor(check) {
    for (let attempt = 0; attempt < 100; attempt++) {
      if (check()) return;
      await new Promise((resolve) => setTimeout(resolve, 20));
    }
    throw new Error('The page did not reach the expected state. Error: ' + get('error').textContent + get('form-error').textContent);
  }
  function fill(name, value) { document.querySelector(`#record-form [name="${name}"]`).value = String(value); }
  async function save() {
    get('record-form').dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true }));
    await waitFor(() => !get('record-dialog').open && !get('refresh').disabled);
  }
  function add(resource) { document.querySelector(`#page-${resource} [data-add]`).click(); }
  function firstAction(resource, label) {
    return [...get(`${resource}-list`).querySelectorAll('button')].find((button) => button.textContent === label);
  }
  async function remove(resource) {
    firstAction(resource, 'Delete').click();
    get('confirm-delete').click();
    await waitFor(() => !get('delete-dialog').open && !get('refresh').disabled);
  }

  try {
    window.eval(script);
    await waitFor(() => get('stat-members').textContent === '0');
    assert.equal(get('getting-started').hidden, false);
    add('workouts');
    assert.equal(get('record-dialog').open, false);
    assert.match(get('notice').textContent, /Add a member first/);

    add('members');
    fill('name', 'Sam Taylor'); fill('email', 'sam@example.com'); fill('age', 24);
    await save();
    assert.equal(get('stat-members').textContent, '1');
    assert.match(get('members-list').textContent, /Sam Taylor/);

    add('workouts');
    fill('activityType', 'Running'); fill('durationMinutes', 30); fill('date', '2026-01-01');
    await save();
    assert.equal(get('stat-minutes').textContent, '30');

    add('challenges');
    fill('name', 'January goal'); fill('target', 60);
    fill('startDate', '2026-01-01'); fill('endDate', '2025-12-31');
    get('record-form').dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true }));
    assert.match(get('form-error').textContent, /end date/);
    fill('endDate', '2026-01-31'); await save();
    assert.equal(get('challenges-list').querySelector('progress').value, 50);

    // This checks that a member with linked records cannot be deleted.
    firstAction('members', 'Delete').click(); get('confirm-delete').click();
    await waitFor(() => get('delete-error').textContent.includes('Delete member workouts'));
    assert.equal(get('delete-dialog').open, true); get('cancel-delete').click();

    firstAction('workouts', 'Edit').click();
    assert.equal(document.querySelector('[name="id"]').readOnly, true);
    fill('durationMinutes', 60); await save();
    assert.equal(get('stat-goals').textContent, '1');
    assert.equal(get('challenges-list').querySelector('progress').value, 100);

    firstAction('challenges', 'Edit').click(); fill('type', 'SESSIONS'); fill('target', 2); await save();
    assert.equal(get('challenges-list').querySelector('progress').value, 50);
    assert.match(get('challenges-list').textContent, /WORKOUT SESSIONS/);

    // This checks that a member name is shown as text, even when it contains HTML.
    firstAction('members', 'Edit').click(); fill('name', '<img src=x onerror=alert(1)>'); await save();
    assert.equal(get('members-list').querySelector('img'), null);
    assert.match(get('members-list').textContent, /<img src=x/);

    add('members'); fill('name', 'Another Member'); fill('email', 'other@example.com'); fill('age', 30); await save();
    get('member-filter').value = '2';
    get('member-filter').dispatchEvent(new window.Event('change'));
    assert.equal(get('stat-minutes').textContent, '0');
    get('member-filter').value = 'all';
    get('member-filter').dispatchEvent(new window.Event('change'));
    assert.equal(get('stat-minutes').textContent, '60');

    await remove('challenges'); await remove('workouts'); await remove('members'); await remove('members');
    assert.equal(get('stat-members').textContent, '0');
    assert.equal(get('getting-started').hidden, false);
    window.location.hash = 'challenges';
    window.dispatchEvent(new window.Event('hashchange'));
    assert.equal(get('page-challenges').hidden, false);
    assert.equal(get('page-overview').hidden, true);
    console.log('PASS: empty states, required member, member/workout/challenge CRUD, date validation, protected deletion, goal types, live progress, text safety, member filter and navigation.');
  } finally {
    // The initial empty-server check ensures these are only this test's records.
    for (const resource of ['challenges', 'workouts', 'members']) {
      const response = await fetch(`${base}/${resource}`);
      for (const record of await response.json()) await fetch(`${base}/${resource}/${record.id}`, { method: 'DELETE' });
    }
    window.close();
  }
})().catch((error) => { console.error(error); process.exitCode = 1; });
