// Shared by the load tests.

import http from 'k6/http';
import { fail } from 'k6';

export const IDENTITY = __ENV.IDENTITY_URL || 'http://localhost:8081';
export const ADVENTURE = __ENV.ADVENTURE_URL || 'http://localhost:8082';
export const CHAT = __ENV.CHAT_URL || 'http://localhost:8083';

export const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const STYLES = ['ADVENTURE', 'BACKPACKING', 'RELAXING', 'FOODIE', 'CULTURE', 'HIKING'];

// The percentiles every run reports.
export const TREND_STATS = ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'];

export function randomInt(min, max) {
  return min + Math.floor(Math.random() * (max - min + 1));
}

export function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

export function isoDate(daysFromNow) {
  return new Date(Date.now() + daysFromNow * 24 * 3600 * 1000).toISOString().slice(0, 10);
}

export function unique(prefix) {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e9)}`;
}

// Request parameters for an authenticated call. `name` becomes a tag, so that k6 reports each
// kind of request separately instead of one figure per distinct URL.
export function as(token, name, extraHeaders) {
  return {
    headers: Object.assign({ Authorization: `Bearer ${token}` }, JSON_HEADERS, extraHeaders || {}),
    tags: { name },
  };
}

// Logs in the load-test users created by identity's `seed` profile and returns their ids and
// access tokens. Call it from setup(): it runs once, and what it returns is handed to every
// virtual user. Access tokens last 15 minutes, so a run must be shorter than that.
export function loginSeedUsers(howMany) {
  const password = __ENV.SEED_PASSWORD;
  const count = howMany || parseInt(__ENV.USERS || '200', 10);
  if (!password) {
    fail('SEED_PASSWORD is required');
  }
  const users = [];
  for (let i = 1; i <= count; i++) {
    const response = http.post(`${IDENTITY}/auth/login`,
      JSON.stringify({ email: `loadtest-${i}@triplor.test`, password }),
      { headers: JSON_HEADERS, tags: { name: 'setup_login' } });
    if (response.status !== 200) {
      fail(`Login of loadtest-${i} failed with ${response.status}. Start the stack with make up-load.`);
    }
    const body = response.json();
    users.push({ id: body.user.id, token: body.accessToken });
  }
  return users;
}
