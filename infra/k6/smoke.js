// End-to-end smoke test: the whole Phase 1 flow, once, with a check on every response.
//
//   two users register and are verified by the admin,
//   one posts a trip, the other finds it by search and asks to join,
//   the organizer accepts, and both see the conversation and exchange messages.
//
// Run it with `make smoke`. Any failed check fails the run (see the threshold below).
// The same flow is the base of the load test in M8.

import http from 'k6/http';
import { check, fail } from 'k6';

const IDENTITY = __ENV.IDENTITY_URL || 'http://localhost:8081';
const ADVENTURE = __ENV.ADVENTURE_URL || 'http://localhost:8082';
const CHAT = __ENV.CHAT_URL || 'http://localhost:8083';
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL;
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD;

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    // Every check must pass.
    checks: ['rate==1.0'],
  },
};

const JSON_HEADERS = { 'Content-Type': 'application/json' };

function unique(prefix) {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e9)}`;
}

function auth(token, extra) {
  return { headers: Object.assign({ Authorization: `Bearer ${token}` }, JSON_HEADERS, extra || {}) };
}

// Checks the status and stops the run with the response body if it is wrong: the later steps
// would only fail in more confusing ways.
function expect(response, status, step) {
  const ok = check(response, { [`${step}: ${status}`]: (r) => r.status === status });
  if (!ok) {
    fail(`${step}: expected ${status}, got ${response.status} ${response.body}`);
  }
  return response.body ? response.json() : null;
}

function isoDate(daysFromNow) {
  return new Date(Date.now() + daysFromNow * 24 * 3600 * 1000).toISOString().slice(0, 10);
}

// Registers a user, has the admin verify them, and returns the user with a token that says so.
function verifiedUser(name, adminToken) {
  const password = unique('pw');
  const registered = expect(
    http.post(`${IDENTITY}/auth/register`,
      JSON.stringify({ email: `${unique('smoke')}@example.com`, password, displayName: name }),
      { headers: JSON_HEADERS }),
    201, `register ${name}`);

  const request = expect(
    http.post(`${IDENTITY}/users/me/verification`, null, auth(registered.accessToken)),
    201, `${name} asks to be verified`);
  expect(
    http.post(`${IDENTITY}/admin/verifications/${request.id}/approve`, null, auth(adminToken)),
    200, `admin verifies ${name}`);

  // The old token still says "not verified". A refreshed one carries the new state.
  const refreshed = expect(
    http.post(`${IDENTITY}/auth/refresh`, JSON.stringify({ refreshToken: registered.refreshToken }),
      { headers: JSON_HEADERS }),
    200, `${name} refreshes the token`);
  check(refreshed, { [`${name} is verified`]: (body) => body.user.verified === true });

  return { id: registered.user.id, token: refreshed.accessToken };
}

export default function () {
  if (!ADMIN_EMAIL || !ADMIN_PASSWORD) {
    fail('ADMIN_EMAIL and ADMIN_PASSWORD are required');
  }

  // --- identity ---
  const admin = expect(
    http.post(`${IDENTITY}/auth/login`, JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
      { headers: JSON_HEADERS }),
    200, 'admin logs in');
  const organizer = verifiedUser('Organizer', admin.accessToken);
  const traveller = verifiedUser('Traveller', admin.accessToken);

  // --- adventure: post a trip ---
  const places = expect(http.get(`${ADVENTURE}/places?q=delhi`, auth(organizer.token)), 200, 'place search');
  if (places.length === 0) {
    fail('No place matches "delhi". Run `make sample-places` (or `make places`) first.');
  }
  const place = places[0];

  const tripBody = JSON.stringify({
    placeId: place.id,
    startDate: isoDate(30),
    endDate: isoDate(34),
    styles: ['FOODIE', 'CULTURE'],
    description: unique('Smoke test trip'),
    capacity: 2,
  });
  const createKey = unique('create');
  const trip = expect(
    http.post(`${ADVENTURE}/adventures`, tripBody, auth(organizer.token, { 'Idempotency-Key': createKey })),
    201, 'organizer posts a trip');
  const replay = http.post(`${ADVENTURE}/adventures`, tripBody, auth(organizer.token, { 'Idempotency-Key': createKey }));
  check(replay, {
    'a retried create returns the same trip': (r) => r.status === 201 && r.json().id === trip.id,
    'and says it was replayed': (r) => r.headers['Idempotent-Replayed'] === 'true',
  });

  // --- adventure: find it and ask to join ---
  const search = expect(
    http.get(`${ADVENTURE}/adventures/search?lat=${place.lat}&lng=${place.lng}&radiusKm=10`
      + `&from=${isoDate(28)}&to=${isoDate(36)}&styles=FOODIE&limit=50`, auth(traveller.token)),
    200, 'traveller searches');
  // A database with many trips at this place may need more than one page; follow the cursor.
  let found = search.items.some((item) => item.id === trip.id);
  let cursor = search.nextCursor;
  for (let page = 0; !found && cursor && page < 20; page++) {
    const next = http.get(`${ADVENTURE}/adventures/search?lat=${place.lat}&lng=${place.lng}&radiusKm=10`
      + `&from=${isoDate(28)}&to=${isoDate(36)}&styles=FOODIE&limit=50&cursor=${cursor}`, auth(traveller.token)).json();
    found = next.items.some((item) => item.id === trip.id);
    cursor = next.nextCursor;
  }
  check(found, { 'the trip is in the search results': (value) => value === true });

  const joinRequest = expect(
    http.post(`${ADVENTURE}/adventures/${trip.id}/join-requests`, JSON.stringify({ message: 'Can I come along?' }),
      auth(traveller.token, { 'Idempotency-Key': unique('join') })),
    201, 'traveller asks to join');
  check(joinRequest, { 'the request is pending': (body) => body.status === 'PENDING' });

  // --- adventure: accept ---
  const accepted = expect(
    http.post(`${ADVENTURE}/join-requests/${joinRequest.id}/accept`, null, auth(organizer.token)),
    200, 'organizer accepts');
  check(accepted, { 'the request is accepted': (body) => body.status === 'ACCEPTED' });

  const tripNow = expect(http.get(`${ADVENTURE}/adventures/${trip.id}`, auth(traveller.token)), 200, 'trip detail');
  check(tripNow, {
    'one seat is taken': (body) => body.seatsTaken === 1,
    "the traveller's request shows as accepted": (body) => body.viewerRequest && body.viewerRequest.status === 'ACCEPTED',
  });
  const members = expect(http.get(`${ADVENTURE}/adventures/${trip.id}/members`, auth(traveller.token)), 200, 'members');
  check(members, { 'organizer and traveller are members': (list) => list.length === 2 });

  // --- chat: both see the conversation and talk ---
  const organizersList = expect(http.get(`${CHAT}/conversations`, auth(organizer.token)), 200, "organizer's conversations");
  const conversation = organizersList.find((item) => item.adventureId === trip.id);
  check(conversation, { 'the trip has a conversation': (value) => value !== undefined });
  if (!conversation) {
    fail('No conversation for the trip');
  }
  const travellersList = expect(http.get(`${CHAT}/conversations`, auth(traveller.token)), 200, "traveller's conversations");
  check(travellersList, { 'the traveller sees it too': (list) => list.some((item) => item.id === conversation.id) });

  const hello = expect(
    http.post(`${CHAT}/conversations/${conversation.id}/messages`,
      JSON.stringify({ clientMessageId: unique('msg'), body: 'Welcome aboard!' }), auth(organizer.token)),
    201, 'organizer sends a message');
  expect(
    http.post(`${CHAT}/conversations/${conversation.id}/messages`,
      JSON.stringify({ clientMessageId: unique('msg'), body: 'Thanks, see you there.' }), auth(traveller.token)),
    201, 'traveller replies');

  const history = expect(
    http.get(`${CHAT}/conversations/${conversation.id}/messages`, auth(traveller.token)), 200, 'history');
  check(history, {
    'history has both messages, newest first': (page) =>
      page.items.length === 2 && page.items[0].body === 'Thanks, see you there.' && page.items[1].id === hello.id,
  });

  expect(
    http.put(`${CHAT}/conversations/${conversation.id}/read`, JSON.stringify({ lastReadMessageId: hello.id }),
      auth(traveller.token)),
    204, 'traveller marks the message read');
  const after = expect(http.get(`${CHAT}/conversations`, auth(traveller.token)), 200, 'conversations after reading');
  check(after.find((item) => item.id === conversation.id), {
    'nothing is unread for the traveller': (item) => item.unreadCount === 0,
  });
}
