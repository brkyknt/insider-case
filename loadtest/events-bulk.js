/**
 * k6 load test: POST /events/bulk (toplu event)
 *
 * Gereksinim: ortalama ~2.000 event/sn, pik ~20.000 event/sn.
 * Hesaplama: sleep(1) ile ~1 iter/sn per VU → 40 VU × 50 event × 1 ≈ 2.000 event/sn;
 * peak 400 VU × 50 × 1 ≈ 20.000 event/sn.
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENTS_PER_REQUEST = parseInt(__ENV.EVENTS_PER_REQUEST || '50', 10);
const BASE_TS = 1771156800;

export const options = {
  stages: [
    { duration: '20s', target: 20 },   // ısınma
    { duration: '1m', target: 40 },   // ~2.000 event/sn (40 VU × 50 event × 1 iter/s)
    { duration: '3m', target: 40 },   // ortalama yük (Gereksinim: average ~2K events/s)
    { duration: '20s', target: 400 },  // peak ~20.000 event/sn (400 VU × 50 event × 1 iter/s)
    { duration: '1m', target: 400 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    http_req_failed: ['rate<0.01'],
  },
};

function buildBulkPayload() {
  const events = [];
  for (let i = 0; i < EVENTS_PER_REQUEST; i++) {
    events.push({
      event_name: 'product_view',
      user_id: `user_${__VU}_${__ITER}_${i}`,
      timestamp: BASE_TS + Math.floor(Math.random() * 86400),
      channel: 'web',
      campaign_id: 'cmp_bulk_loadtest',
    });
  }
  return JSON.stringify({ events });
}

export default function () {
  const payload = buildBulkPayload();
  const res = http.post(`${BASE_URL}/events/bulk`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  check(res, { 'status 202': (r) => r.status === 202 });

  // ~1 iter/sn per VU için (40 VU → ~2K event/sn, 400 VU → ~20K event/sn)
  sleep(1);
}
