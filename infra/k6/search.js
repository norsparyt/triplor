// Load test: discovery search.
//
// Virtual users search around random big cities with random date windows, radii and styles.
// One in four also asks for the second page. Each waits 100 to 300 ms between searches.
// The load rises in two steps, to 20 and then to 50 users (VUS=200 makes it 80 and 200).
//
//   make up-load && make seed-adventures && make load-search
//
// Read search_ms in the summary. The untagged http_req_duration includes the logins done
// during setup.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { ADVENTURE, STYLES, TREND_STATS, as, isoDate, loginSeedUsers, pick, randomInt } from './lib.js';

// Response times of the searches alone, with every percentile. The built-in
// http_req_duration also contains the logins done during setup.
const searchMs = new Trend('search_ms', true);
const nextPageMs = new Trend('search_next_page_ms', true);

// The second step of the ramp. The first step is 40% of it. Override with VUS=200, for example.
const PEAK_VUS = parseInt(__ENV.VUS || '50', 10);

export const options = {
  setupTimeout: '300s',
  summaryTrendStats: TREND_STATS,
  scenarios: {
    search: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: Math.round(PEAK_VUS * 0.4) },
        { duration: '60s', target: Math.round(PEAK_VUS * 0.4) },
        { duration: '15s', target: PEAK_VUS },
        { duration: '60s', target: PEAK_VUS },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    // Naming a tag in a threshold is what makes k6 report it on its own line.
    'http_req_duration{name:search}': ['p(95)<1000'],
    'http_req_duration{name:search_next_page}': ['p(95)<1000'],
    'http_req_failed{name:search}': ['rate<0.01'],
    'http_reqs{name:search}': ['count>0'],
    checks: ['rate>0.99'],
  },
};

// Centres of large urban areas on every continent. The seeded trips are spread over the 5,000
// most populous places, so each of these has trips nearby.
const CENTRES = [
  [28.65, 77.23], [19.07, 72.88], [12.97, 77.59], [22.57, 88.36], [13.08, 80.28], [17.38, 78.47],
  [31.23, 121.47], [39.90, 116.40], [23.13, 113.26], [35.69, 139.69], [37.57, 126.98], [13.75, 100.50],
  [-6.21, 106.85], [14.60, 120.98], [24.86, 67.01], [23.81, 90.41], [41.01, 28.98], [30.04, 31.24],
  [6.52, 3.38], [-26.20, 28.05], [-1.29, 36.82], [51.51, -0.13], [48.85, 2.35], [52.52, 13.40],
  [40.42, -3.70], [41.90, 12.50], [55.75, 37.62], [40.71, -74.01], [34.05, -118.24], [41.88, -87.63],
  [19.43, -99.13], [-23.55, -46.63], [-34.60, -58.38], [4.71, -74.07], [-12.05, -77.04], [-33.87, 151.21],
];
const RADII_KM = [25, 50, 100, 250];

export function setup() {
  return { users: loginSeedUsers() };
}

export default function (data) {
  const user = data.users[(__VU - 1) % data.users.length];
  const [lat, lng] = pick(CENTRES);
  const from = randomInt(0, 150);
  let query = `lat=${lat}&lng=${lng}&radiusKm=${pick(RADII_KM)}`
    + `&from=${isoDate(from)}&to=${isoDate(from + randomInt(3, 21))}&limit=20`;
  if (Math.random() < 0.5) {
    const styles = Math.random() < 0.5 ? [pick(STYLES)] : [pick(STYLES), pick(STYLES)];
    query += `&styles=${styles.join(',')}`;
  }

  const response = http.get(`${ADVENTURE}/adventures/search?${query}`, as(user.token, 'search'));
  searchMs.add(response.timings.duration);
  const ok = check(response, { 'search: 200': (r) => r.status === 200 });

  if (ok && Math.random() < 0.25) {
    const cursor = response.json().nextCursor;
    if (cursor) {
      const next = http.get(`${ADVENTURE}/adventures/search?${query}&cursor=${cursor}`, as(user.token, 'search_next_page'));
      nextPageMs.add(next.timings.duration);
      check(next, { 'next page: 200': (r) => r.status === 200 });
    }
  }
  sleep(randomInt(100, 300) / 1000);
}
