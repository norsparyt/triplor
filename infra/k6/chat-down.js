// Failure demonstration: what an accept does while chat is not running, and what the retry
// does once chat is back. Driven by infra/scripts/chat-down-demo.sh, which stops and starts
// the chat container around the two phases.
//
//   PHASE=down   posts a trip, asks to join, accepts. Expects 502 CHAT_UNAVAILABLE with the
//                seat nevertheless taken, and prints the ids for the second phase.
//   PHASE=retry  accepts the same request again, until it succeeds. Expects 200 and still
//                exactly one seat.

import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { ADVENTURE, CHAT, as, isoDate, loginSeedUsers, unique } from './lib.js';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { checks: ['rate==1.0'] },
};

export function setup() {
  return { users: loginSeedUsers(2) };
}

function seatsTaken(tripId, token) {
  return http.get(`${ADVENTURE}/adventures/${tripId}`, as(token, 'trip')).json().seatsTaken;
}

export default function (data) {
  const [organizer, traveller] = data.users;

  if (__ENV.PHASE === 'retry') {
    // Chat is healthy again, but adventure may not reach it at once: while the chat container
    // was stopped its name did not resolve, and the JVM remembers a failed lookup for ten
    // seconds. So the retry is repeated, once a second, until it gets through.
    let again;
    let attempts = 0;
    const started = Date.now();
    do {
      if (attempts > 0) {
        sleep(1);
      }
      again = http.post(`${ADVENTURE}/join-requests/${__ENV.REQUEST_ID}/accept`, null, as(organizer.token, 'accept'));
      attempts++;
    } while (again.status !== 200 && attempts < 30);
    console.log(`retry succeeded on attempt ${attempts}, ${Math.round((Date.now() - started) / 1000)} s after chat was healthy`);
    check(again, { 'with chat back, a retry returns 200 within 30 seconds': (r) => r.status === 200 });
    check(seatsTaken(__ENV.ADVENTURE_ID, organizer.token), { 'still exactly one seat taken': (seats) => seats === 1 });
    const conversations = http.get(`${CHAT}/conversations`, as(traveller.token, 'conversations')).json();
    check(conversations, {
      'the traveller is now in the conversation': (list) => list.some((c) => c.adventureId === __ENV.ADVENTURE_ID),
    });
    return;
  }

  const place = http.get(`${ADVENTURE}/places?q=delhi`, as(organizer.token, 'places')).json()[0];
  if (!place) {
    fail('No place matches "delhi".');
  }
  const trip = http.post(`${ADVENTURE}/adventures`,
    JSON.stringify({ placeId: place.id, startDate: isoDate(40), endDate: isoDate(44), styles: ['FOODIE'],
      description: unique('[load] chat-down demo'), capacity: 3 }),
    as(organizer.token, 'create_trip', { 'Idempotency-Key': unique('load-create') })).json();
  const request = http.post(`${ADVENTURE}/adventures/${trip.id}/join-requests`, null,
    as(traveller.token, 'join_request', { 'Idempotency-Key': unique('load-join') })).json();

  const accepted = http.post(`${ADVENTURE}/join-requests/${request.id}/accept`, null, as(organizer.token, 'accept'));
  check(accepted, {
    'with chat down, accept returns 502': (r) => r.status === 502,
    'with the code CHAT_UNAVAILABLE': (r) => r.json().code === 'CHAT_UNAVAILABLE',
  });
  check(seatsTaken(trip.id, organizer.token), { 'but the seat is taken': (seats) => seats === 1 });
  console.log(`accept answered ${accepted.status} after ${Math.round(accepted.timings.duration)} ms`);
  console.log(`DEMO_STATE ${trip.id} ${request.id}`);
}
