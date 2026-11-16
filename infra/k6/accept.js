// Load test: the join flow, ending in accept.
//
// Each iteration one organizer posts a five-seat trip, five travellers ask to join, and the
// organizer accepts all five. The trip must end FULL with five seats taken. There is no
// waiting between requests.
//
//   make up-load && make load-accept
//
// The line to read is accept_ms. In Phase 1 an accept includes a
// synchronous call to chat, so it is as slow as chat is. Run it again behind Toxiproxy
// (make up-chaos, infra/scripts/chaos.sh slow 500) to see the caller pay for a slow
// dependency. Phase 2 repeats exactly these runs.

import http from 'k6/http';
import { check, fail } from 'k6';
import { Trend } from 'k6/metrics';
import { ADVENTURE, TREND_STATS, as, isoDate, loginSeedUsers, randomInt, unique } from './lib.js';

// Response times per kind of request, with every percentile.
const createTripMs = new Trend('create_trip_ms', true);
const joinRequestMs = new Trend('join_request_ms', true);
const acceptMs = new Trend('accept_ms', true);

const VUS = parseInt(__ENV.VUS || '10', 10);
const SEATS = 5;

export const options = {
  setupTimeout: '300s',
  summaryTrendStats: TREND_STATS,
  scenarios: {
    accept: { executor: 'constant-vus', vus: VUS, duration: __ENV.DURATION || '90s', gracefulStop: '30s' },
  },
  thresholds: {
    'http_req_duration{name:accept}': ['p(95)<5000'],
    'http_req_duration{name:create_trip}': ['p(95)<5000'],
    'http_req_duration{name:join_request}': ['p(95)<5000'],
    'http_req_failed{name:accept}': ['rate<0.01'],
    'http_reqs{name:accept}': ['count>0'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const users = loginSeedUsers();
  // Every virtual user needs an organizer and five travellers of its own.
  if (VUS * (SEATS + 1) > users.length) {
    fail(`${VUS} virtual users need ${VUS * (SEATS + 1)} load-test users; there are ${users.length}`);
  }
  const places = http.get(`${ADVENTURE}/places?q=delhi`, as(users[0].token, 'setup_places')).json();
  if (!places || places.length === 0) {
    fail('No place matches "delhi". Run make sample-places or make places first.');
  }
  return { users, placeId: places[0].id };
}

export default function (data) {
  const first = (__VU - 1) * (SEATS + 1);
  const organizer = data.users[first];
  const travellers = data.users.slice(first + 1, first + 1 + SEATS);

  const start = randomInt(30, 120);
  const created = http.post(`${ADVENTURE}/adventures`,
    JSON.stringify({
      placeId: data.placeId,
      startDate: isoDate(start),
      endDate: isoDate(start + 4),
      styles: ['FOODIE'],
      description: unique('[load] trip'),
      capacity: SEATS,
    }),
    as(organizer.token, 'create_trip', { 'Idempotency-Key': unique('load-create') }));
  createTripMs.add(created.timings.duration);
  if (!check(created, { 'create trip: 201': (r) => r.status === 201 })) {
    return;
  }
  const tripId = created.json().id;

  const requestIds = [];
  for (const traveller of travellers) {
    const asked = http.post(`${ADVENTURE}/adventures/${tripId}/join-requests`, null,
      as(traveller.token, 'join_request', { 'Idempotency-Key': unique('load-join') }));
    joinRequestMs.add(asked.timings.duration);
    if (check(asked, { 'join request: 201': (r) => r.status === 201 })) {
      requestIds.push(asked.json().id);
    }
  }

  for (const requestId of requestIds) {
    const accepted = http.post(`${ADVENTURE}/join-requests/${requestId}/accept`, null, as(organizer.token, 'accept'));
    acceptMs.add(accepted.timings.duration);
    check(accepted, { 'accept: 200': (r) => r.status === 200 });
  }

  const trip = http.get(`${ADVENTURE}/adventures/${tripId}`, as(organizer.token, 'verify_trip'));
  check(trip, {
    'the trip is full with five seats taken': (r) =>
      r.status === 200 && r.json().seatsTaken === SEATS && r.json().status === 'FULL',
  });
}
