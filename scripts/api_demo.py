#!/usr/bin/env python3
"""Verify HTTP CRUD against an already running FitTrack server; remove demo records."""
import http.cookiejar
import json
import os
import urllib.error
import urllib.request
import urllib.parse

BASE = os.environ.get('FITTRACK_API_URL', 'http://127.0.0.1:8080').rstrip('/')
ID = 900001
HEADERS = {'Content-Type': 'application/json', 'X-FitTrack-Request': 'true'}
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
username = os.environ.get('FITTRACK_ADMIN_USER', '')
password = os.environ.get('FITTRACK_ADMIN_PASSWORD', '')
if username or password:
    if not username or not password:
        raise ValueError('Set both FITTRACK_ADMIN_USER and FITTRACK_ADMIN_PASSWORD.')
    address = urllib.parse.urlparse(BASE)
    if address.scheme != 'https' and address.hostname not in ('localhost', '127.0.0.1', '::1'):
        raise ValueError('Use HTTPS before sending sign-in details to an online server.')
    # This signs in once. The cookie jar sends the session cookie with later requests.
    login = urllib.request.Request(BASE + '/auth/login',
        data=json.dumps({'username': username, 'password': password}).encode(),
        headers=HEADERS, method='POST')
    with opener.open(login, timeout=15) as response:
        if response.status != 200:
            raise RuntimeError('FitTrack sign-in failed.')


def request(method, path, expected, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, method=method,
                                 headers=HEADERS)
    try:
        response = opener.open(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        text = response.read().decode()
        print(f'{method} {path} -> {response.status} {text}')
        if response.status != expected:
            raise RuntimeError(f'Expected {expected}, received {response.status}')
        return json.loads(text) if text else None


# This stops the demo before any changes if its reserved ID is already in use.
for resource in ('members', 'workouts', 'challenges'):
    request('GET', f'/{resource}/{ID}', 404)

created = []
try:
    member = dict(id=ID, name='Demo Member', email='demo900001@example.com', age=25)
    workout = dict(id=ID, memberId=ID, activityType='Running', durationMinutes=30, date='2026-01-01')
    challenge = dict(id=ID, memberId=ID, name='Demo goal', type='MINUTES', target=60,
                     startDate='2026-01-01', endDate='2026-01-31')
    for resource, body in (('members', member), ('workouts', workout), ('challenges', challenge)):
        request('POST', f'/{resource}', 201, body)
        created.append(resource)
        request('GET', f'/{resource}/{ID}', 200)
        request('GET', f'/{resource}', 200)
    request('POST', '/members', 409, member)
    assert request('GET', f'/challenges/{ID}/progress', 200)['percentage'] == 50
    request('DELETE', f'/members/{ID}', 409)
    workout['durationMinutes'] = 60
    request('PUT', f'/workouts/{ID}', 200, workout)
    assert request('GET', f'/challenges/{ID}/progress', 200)['completed'] is True
    challenge['type'] = 'SESSIONS'
    challenge['target'] = 2
    request('PUT', f'/challenges/{ID}', 200, challenge)
    assert request('GET', f'/challenges/{ID}/progress', 200)['achieved'] == 1
    member['age'] = 26
    request('PUT', f'/members/{ID}', 200, member)
    assert request('GET', f'/members/{ID}/summary', 200) == dict(count=1, totalMinutes=60, longestMinutes=60)
finally:
    for resource in reversed(created):
        request('DELETE', f'/{resource}/{ID}', 204)
        request('GET', f'/{resource}/{ID}', 404)
print('HTTP CRUD and progress verification passed; demo records removed.')

if username and password:
    request('POST', '/auth/logout', 204)
