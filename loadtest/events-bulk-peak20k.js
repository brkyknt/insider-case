/**
 * k6 load test: POST /events/bulk – 20K event/sn PİK odaklı
 *
 * Sadece pik yükü doğrular: ~20.000 event/sn.
 * 400 VU × 50 event × 1 iter/s ≈ 20.000 event/sn.
 *
 * Kısa ısınma, uzun pik süresi, event/sn custom metriği ile.
 *
 * Kullanım: k6 run loadtest/events-bulk-peak20k.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const eventsTotal = new Counter('events');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENTS_PER_REQUEST = parseInt(__ENV.EVENTS_PER_REQUEST || '50', 10);
const BASE_TS = 1771156800;

export const options = {
  scenarios: {
    peak_20k: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: 400 },   // ısınma + pik ramp
        { duration: '3m', target: 400 },    // 3 dk pik sürdürme
        { duration: '15s', target: 0 },     // ramp down
      ],
      startTime: '0s',
      gracefulRampDown: '15s',
      gracefulStop: '15s',
      exec: 'peakLoad',
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<1000', 'p(99)<2000'],
    'http_req_failed': ['rate<0.01'],
    'events': ['rate>15000'],               // en az ~15K event/sn (pik aşamasında)
  },
};

export function peakLoad() {
  const payload = buildBulkPayload();
  const res = http.post(`${BASE_URL}/events/bulk`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  check(res, { 'status 202': (r) => r.status === 202 });

  eventsTotal.add(EVENTS_PER_REQUEST);

  // ~1 iter/sn per VU (400 VU × 50 event ≈ 20K event/sn)
  sleep(1);
}

function buildBulkPayload() {
  const events = [];
  for (let i = 0; i < EVENTS_PER_REQUEST; i++) {
    events.push({
      event_name: 'product_view',
      user_id: `user_${__VU}_${__ITER}_${i}`,
      timestamp: BASE_TS + Math.floor(Math.random() * 86400),
      channel: 'web',
      campaign_id: 'cmp_peak20k_loadtest',
    });
  }
  return JSON.stringify({ events });
}
