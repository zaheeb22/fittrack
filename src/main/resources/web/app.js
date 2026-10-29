'use strict';

// 1. This keeps a copy of the records for all four pages.
const records = { members: [], workouts: [], challenges: [], progress: new Map() };
const pageNames = ['overview', 'members', 'workouts', 'challenges'];
let selectedMember = 'all';
let editingRecord = null;
let deletingRecord = null;
let saving = false;
let deleting = false;
let loading = false;
let recordsReady = false;
const find = (id) => document.getElementById(id);
const recordDialog = find('record-dialog');
const deleteDialog = find('delete-dialog');

// This creates text safely. A member name is treated as text, never as HTML code.
function make(tag, className, text) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text !== undefined) element.textContent = text;
  return element;
}

function showMessage(id, message) {
  find(id).textContent = message;
  find(id).hidden = !message;
}

// This sends requests to the Java API using the webpage address.
async function api(path, method = 'GET', body) {
  let response;
  try {
    response = await fetch(path, {
      method,
      headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
  } catch (error) {
    throw new Error('Cannot reach FitTrack. Check that the Java application is still running.');
  }
  const result = response.status === 204 ? null : await response.json();
  if (!response.ok) throw new Error(result.error || 'The request could not be completed.');
  return result;
}

// This loads the three record lists and their challenge progress from Java.
async function loadRecords() {
  if (loading) return;
  loading = true;
  find('refresh').disabled = true;
  find('refresh').textContent = 'Loading…';
  showMessage('error', '');
  try {
    const [members, workouts, challenges] = await Promise.all([
      api('/members'), api('/workouts'), api('/challenges')
    ]);
    const progress = await Promise.all(challenges.map(async (challenge) => [
      challenge.id, await api(`/challenges/${challenge.id}/progress`)
    ]));
    // The screen data changes only after all requests finish successfully.
    records.members = members;
    records.workouts = workouts;
    records.challenges = challenges;
    records.progress = new Map(progress);
    recordsReady = true;
    updateMemberFilter();
    renderPages();
  } catch (error) {
    showMessage('error', error.message + ' Use Refresh to try again.');
    if (!recordsReady) {
      for (const id of ['recent-workouts', 'overview-challenges', 'members-list', 'workouts-list', 'challenges-list']) {
        find(id).replaceChildren(make('p', 'empty', 'Records could not be loaded. Please try Refresh.'));
      }
    }
  } finally {
    loading = false;
    find('refresh').disabled = false;
    find('refresh').textContent = '↻ Refresh';
  }
}

// 2. These functions switch pages and format names, dates and tables.
function showPage() {
  const requestedPage = location.hash.slice(1) || 'overview';
  const page = pageNames.includes(requestedPage) ? requestedPage : 'overview';
  for (const name of pageNames) find(`page-${name}`).hidden = name !== page;
  document.querySelectorAll('[data-page]').forEach((link) => {
    if (link.dataset.page === page) link.setAttribute('aria-current', 'page');
    else link.removeAttribute('aria-current');
  });
  document.title = `FitTrack | ${page.charAt(0).toUpperCase() + page.slice(1)}`;
}

function memberName(id) {
  return records.members.find((member) => member.id === id)?.name || `Member ${id}`;
}

function showDate(value) {
  // Midday avoids a date moving to yesterday because of time zone conversion.
  return new Date(value + 'T12:00:00').toLocaleDateString('en-GB', {
    day: 'numeric', month: 'short', year: 'numeric'
  });
}

function updateMemberFilter() {
  const filter = find('member-filter');
  if (selectedMember !== 'all' && !records.members.some((m) => String(m.id) === selectedMember)) {
    selectedMember = 'all';
  }
  filter.replaceChildren(new Option('All members', 'all'));
  records.members.forEach((member) => filter.add(new Option(member.name, String(member.id))));
  filter.value = selectedMember;
}

function actionButtons(resource, record) {
  const actions = make('div', 'row-actions');
  const edit = make('button', 'button quiet', 'Edit');
  edit.type = 'button';
  edit.setAttribute('aria-label', `Edit ${record.name || record.activityType}`);
  edit.addEventListener('click', () => openForm(resource, record));
  const remove = make('button', 'button quiet delete-link', 'Delete');
  remove.type = 'button';
  remove.setAttribute('aria-label', `Delete ${record.name || record.activityType}`);
  remove.addEventListener('click', () => openDelete(resource, record));
  actions.append(edit, remove);
  return actions;
}

function drawTable(containerId, headings, rows, emptyMessage) {
  const container = find(containerId);
  if (!rows.length) {
    container.replaceChildren(make('p', 'empty', emptyMessage));
    return;
  }
  const table = make('table');
  const head = make('thead');
  const headerRow = make('tr');
  headings.forEach((heading) => {
    const cell = make('th', '', heading);
    cell.scope = 'col';
    headerRow.append(cell);
  });
  head.append(headerRow);
  const body = make('tbody');
  rows.forEach((row) => {
    const tr = make('tr');
    row.forEach((value) => {
      const cell = make('td');
      if (value instanceof Node) cell.append(value);
      else cell.textContent = String(value);
      tr.append(cell);
    });
    body.append(tr);
  });
  table.append(head, body);
  container.replaceChildren(table);
}

function progressDisplay(challenge) {
  const value = records.progress.get(challenge.id);
  const units = challenge.type === 'MINUTES' ? 'min' : 'sessions';
  const block = make('div', 'goal-progress');
  const label = make('div', 'progress-label');
  label.append(make('strong', '', `${value.achieved} / ${value.target} ${units}`),
    make('span', '', `${Math.round(value.percentage)}%`));
  const bar = make('progress');
  bar.max = 100;
  bar.value = value.percentage;
  bar.setAttribute('aria-label', `${challenge.name} progress`);
  block.append(label, bar);
  if (value.completed) block.append(make('span', 'completed', '✓ Goal reached'));
  return block;
}

// 3. This displays the four pages using the latest records.
function renderPages() {
  const workouts = records.workouts.filter((w) => selectedMember === 'all' || String(w.memberId) === selectedMember);
  const challenges = records.challenges.filter((c) => selectedMember === 'all' || String(c.memberId) === selectedMember);
  find('stat-members').textContent = records.members.length;
  find('stat-workouts').textContent = workouts.length;
  find('stat-minutes').textContent = workouts.reduce((total, w) => total + w.durationMinutes, 0).toLocaleString('en-GB');
  find('stat-goals').textContent = challenges.filter((c) => records.progress.get(c.id).completed).length;
  find('goals-caption').textContent = `Out of ${challenges.length} challenges`;
  find('getting-started').hidden = records.members.length > 0;

  const recent = [...workouts].sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id).slice(0, 4);
  const recentNodes = recent.map((workout) => {
    const row = make('div', 'activity-row');
    const details = make('div');
    details.append(make('strong', '', workout.activityType), make('small', '', `${memberName(workout.memberId)} · ${showDate(workout.date)}`));
    row.append(make('span', 'activity-icon', '↗'), details, make('span', '', `${workout.durationMinutes} min`));
    return row;
  });
  find('recent-workouts').replaceChildren(...(recentNodes.length ? recentNodes : [make('p', 'empty', 'No workouts yet. Record a session to get started.')]));

  const miniGoals = challenges.slice(0, 3).map((challenge) => {
    const card = make('div', 'mini-goal');
    card.append(make('strong', '', challenge.name), make('p', '', memberName(challenge.memberId)), progressDisplay(challenge));
    return card;
  });
  find('overview-challenges').replaceChildren(...(miniGoals.length ? miniGoals : [make('p', 'empty', 'Your next goal goes here. Add a challenge to start.')]));

  find('members-count').textContent = `${records.members.length} members`;
  drawTable('members-list', ['Name', 'Email', 'Age', 'Record number', 'Actions'], records.members.map((m) => [
    m.name, m.email, m.age, `#${m.id}`, actionButtons('members', m)
  ]), 'No members yet. Use Add member to create your first member.');
  find('workouts-count').textContent = `${records.workouts.length} workouts`;
  drawTable('workouts-list', ['Activity', 'Member', 'Date', 'Minutes', 'Actions'], [...records.workouts].sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id).map((w) => [
    w.activityType, memberName(w.memberId), showDate(w.date), w.durationMinutes, actionButtons('workouts', w)
  ]), 'No workouts yet. Add a member, then record a workout.');

  const goalCards = records.challenges.map((challenge) => {
    const card = make('article', 'challenge-card');
    card.append(make('span', 'goal-type', challenge.type === 'MINUTES' ? 'EXERCISE MINUTES' : 'WORKOUT SESSIONS'),
      make('h3', '', challenge.name), make('p', '', memberName(challenge.memberId)), progressDisplay(challenge),
      make('p', 'goal-dates', `${showDate(challenge.startDate)} – ${showDate(challenge.endDate)}`),
      actionButtons('challenges', challenge));
    return card;
  });
  find('challenges-list').replaceChildren(...(goalCards.length ? goalCards : [make('p', 'empty', 'No challenges yet. Add a member, then choose a goal.')]));
}

// 4. This creates labelled forms. Java also checks the values before saving.
function addField(name, label, type, value, options = {}) {
  const wrapper = make('label', 'form-field');
  wrapper.append(make('span', '', label));
  let input;
  if (type === 'select') {
    input = make('select');
    options.choices.forEach(([text, optionValue]) => input.add(new Option(text, String(optionValue))));
    input.value = String(value);
  } else {
    input = make('input');
    input.type = type;
    input.value = value;
  }
  input.name = name;
  input.required = true;
  if (type === 'number') { input.min = '1'; input.max = '2147483647'; input.step = '1'; }
  if (options.max) input.max = options.max;
  if (options.maxLength) input.maxLength = options.maxLength;
  if (options.readOnly) input.readOnly = true;
  wrapper.append(input);
  if (options.help) wrapper.append(make('small', '', options.help));
  find('form-fields').append(wrapper);
}

function todayISO() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

function openForm(resource, record = null) {
  showMessage('notice', '');
  if (!recordsReady || loading) { showMessage('error', 'Wait for records to load, then try again.'); return; }
  if (resource !== 'members' && !records.members.length) {
    showMessage('notice', 'Add a member first. Each workout and challenge belongs to a member.');
    location.hash = 'members';
    return;
  }
  editingRecord = { resource, id: record?.id ?? null };
  const names = { members: 'member', workouts: 'workout', challenges: 'challenge' };
  find('form-title').textContent = `${record ? 'Edit' : 'Add'} ${names[resource]}`;
  find('form-help').textContent = record ? 'Change the details below, then save your changes.' : 'Fill in the details below to add a record.';
  find('save-record').textContent = record ? 'Save changes' : 'Add record';
  find('form-fields').replaceChildren();
  showMessage('form-error', '');
  const nextId = records[resource].reduce((highest, item) => Math.max(highest, item.id), 0) + 1;
  addField('id', 'Record number', 'number', record?.id ?? nextId, {
    readOnly: !!record, help: record ? 'The record number stays the same when editing.' : 'A number has been suggested. It must not already be in use.'
  });
  if (resource === 'members') {
    addField('name', 'Full name', 'text', record?.name ?? '', { maxLength: 200 });
    addField('email', 'Email address', 'email', record?.email ?? '', { maxLength: 254 });
    addField('age', 'Age', 'number', record?.age ?? '');
  } else {
    addField('memberId', 'Member', 'select', record?.memberId ?? records.members[0].id, {
      choices: records.members.map((m) => [`${m.name} (#${m.id})`, m.id])
    });
    if (resource === 'workouts') {
      addField('activityType', 'Activity name', 'text', record?.activityType ?? '', { maxLength: 200, help: 'For example, running, cycling or walking.' });
      addField('durationMinutes', 'Duration in minutes', 'number', record?.durationMinutes ?? '');
      addField('date', 'Workout date', 'date', record?.date ?? todayISO(), { max: todayISO(), help: 'Choose the date the workout was completed.' });
    } else {
      addField('name', 'Challenge name', 'text', record?.name ?? '', { maxLength: 200 });
      addField('type', 'Goal type', 'select', record?.type ?? 'MINUTES', { choices: [['Exercise minutes', 'MINUTES'], ['Workout sessions', 'SESSIONS']] });
      addField('target', 'Target', 'number', record?.target ?? '', { help: 'Enter the number of minutes or sessions you want to complete.' });
      addField('startDate', 'Start date', 'date', record?.startDate ?? todayISO());
      addField('endDate', 'End date', 'date', record?.endDate ?? todayISO());
    }
  }
  recordDialog.showModal();
}

async function saveRecord(event) {
  event.preventDefault();
  if (saving) return;
  if (loading) { showMessage('form-error', 'Wait for the refresh to finish, then save again.'); return; }
  const form = find('record-form');
  if (!form.reportValidity()) return;
  const body = Object.fromEntries(new FormData(form));
  for (const key of ['id', 'memberId', 'age', 'durationMinutes', 'target']) {
    if (key in body) body[key] = Number(body[key]);
  }
  if (body.endDate && body.endDate < body.startDate) {
    showMessage('form-error', 'The end date must be on or after the start date.');
    return;
  }
  saving = true;
  find('save-record').disabled = true;
  find('close-form').disabled = true;
  find('cancel-form').disabled = true;
  showMessage('form-error', '');
  try {
    const { resource, id } = editingRecord;
    await api(id === null ? `/${resource}` : `/${resource}/${id}`, id === null ? 'POST' : 'PUT', body);
    recordDialog.close();
    showMessage('notice', id === null ? 'Record added successfully.' : 'Changes saved successfully.');
    await loadRecords();
  } catch (error) {
    showMessage('form-error', error.message);
  } finally {
    saving = false;
    find('save-record').disabled = false;
    find('close-form').disabled = false;
    find('cancel-form').disabled = false;
  }
}

// 5. This confirms deletions. The server protects members with linked records.
function openDelete(resource, record) {
  deletingRecord = { resource, id: record.id };
  find('delete-message').textContent = `Delete “${record.name || record.activityType}”? This cannot be undone.` +
    (resource === 'members' ? ' Delete this member’s workouts and challenges first.' : '');
  showMessage('delete-error', '');
  deleteDialog.showModal();
}

async function deleteRecord() {
  if (deleting) return;
  if (loading) { showMessage('delete-error', 'Wait for the refresh to finish, then delete again.'); return; }
  deleting = true;
  find('confirm-delete').disabled = true;
  find('cancel-delete').disabled = true;
  try {
    await api(`/${deletingRecord.resource}/${deletingRecord.id}`, 'DELETE');
    deleteDialog.close();
    showMessage('notice', 'Record deleted successfully.');
    await loadRecords();
  } catch (error) {
    showMessage('delete-error', error.message);
  } finally {
    deleting = false;
    find('confirm-delete').disabled = false;
    find('cancel-delete').disabled = false;
  }
}

// 6. This connects button actions and loads the first page.
find('today').textContent = new Date().toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'long', year: 'numeric' });
window.addEventListener('hashchange', showPage);
find('refresh').addEventListener('click', loadRecords);
find('member-filter').addEventListener('change', (event) => { selectedMember = event.target.value; renderPages(); });
document.querySelectorAll('[data-add]').forEach((button) => button.addEventListener('click', () => openForm(button.dataset.add)));
find('record-form').addEventListener('submit', saveRecord);
find('close-form').addEventListener('click', () => recordDialog.close());
find('cancel-form').addEventListener('click', () => recordDialog.close());
find('cancel-delete').addEventListener('click', () => deleteDialog.close());
find('confirm-delete').addEventListener('click', deleteRecord);
recordDialog.addEventListener('cancel', (event) => { if (saving) event.preventDefault(); });
deleteDialog.addEventListener('cancel', (event) => { if (deleting) event.preventDefault(); });
showPage();
loadRecords();
