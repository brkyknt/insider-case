/**
 * k6 smoke test: Kısa süreli düşük yük, API'nin ayakta olduğunu doğrular.
 * Çalıştırma: k6 run loadtest/smoke.js
 */
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  vus: 5,
  duration: '15s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const res = http.post(
    `${BASE_URL}/events`,
    JSON.stringify({
      event_name: 'product_view',
      user_id: `user_smoke_${__VU}_${__ITER}`,
      timestamp: 1771156800 + __ITER,
      channel: 'web',
      campaign_id: 'cmp_smoke',
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(res, { 'status 202': (r) => r.status === 202 });
}
