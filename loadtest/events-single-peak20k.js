/**
 * k6 load test: POST /events (tek event) – 20K event/sn PİK odaklı
 *
 * Sadece pik yükü doğrular: ~20.000 req/sn (= event/sn, tek event/istek).
 * 2500 VU ile hedef ~20K req/sn.
 *
 * Kısa ısınma, uzun pik süresi, events custom metriği ile.
 *
 * Kullanım: k6 run loadtest/events-single-peak20k.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const eventsTotal = new Counter('events');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const BASE_TS = 1771156800;

export const options = {
  scenarios: {
    peak_20k: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 2500 },  // ısınma + pik ramp
        { duration: '3m', target: 2500 },   // 3 dk pik sürdürme
        { duration: '20s', target: 0 },     // ramp down
      ],
      startTime: '0s',
      gracefulRampDown: '20s',
      gracefulStop: '20s',
      exec: 'peakLoad',
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<300', 'p(99)<500'],
    'http_req_failed': ['rate<0.01'],
    'events': ['rate>15000'],               // en az ~15K event/sn (1 event = 1 req)
  },
};

export function peakLoad() {
  const payload = JSON.stringify({
    event_name: 'product_view',
    channel: 'web',
    campaign_id: 'cmp_peak20k_loadtest',
    user_id: `user_${__VU}_${__ITER}`,
    timestamp: BASE_TS + Math.floor(Math.random() * 86400),
    tags: ['loadtest', 'k6', 'peak20k'],
    metadata: { source: 'k6', iteration: __ITER },
  });

  const res = http.post(`${BASE_URL}/events`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  check(res, { 'status 202': (r) => r.status === 202 });

  eventsTotal.add(1);

  // Kısa sleep: yüksek req/s (gerçek değer http_reqs /s ile ölçülür)
  sleep(Math.random() * 0.1 + 0.02);
}
