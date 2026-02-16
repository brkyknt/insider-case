/**
 * k6 load test: POST /events (tek event)
 *
 * ortalama ~2.000 req/sn, pik ~20.000 req/sn.
 * 400 VU (kısa sleep) → hedef ~2K req/sn; 2500 VU → hedef ~20K req/sn.
 * Gerçek throughput sunucu gecikmesine bağlı (http_reqs /s ile doğrulanır).
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const BASE_TS = 1771156800;

export const options = {
  stages: [
    { duration: '30s', target: 100 },   // ısınma
    { duration: '1m', target: 400 },   // ortalama yük (~2K req/sn hedefi)
    { duration: '3m', target: 400 },
    { duration: '20s', target: 2500 }, // peak (~20K req/sn hedefi)
    { duration: '1m', target: 2500 },  // peak sürsün
    { duration: '30s', target: 0 },    // iniş
  ],
  thresholds: {
    http_req_duration: ['p(95)<300', 'p(99)<500'], // very low latency (Assessment)
    http_req_failed: ['rate<0.01'],                  // yük altında bloklama değil, hata kabul edilebilir sınırda
  },
};

export default function () {
  const payload = JSON.stringify({
    event_name: 'product_view',
    channel: 'web',
    campaign_id: 'cmp_loadtest',
    user_id: `user_${__VU}_${__ITER}`,
    timestamp: BASE_TS + Math.floor(Math.random() * 86400),
    tags: ['loadtest', 'k6'],
    metadata: { source: 'k6', iteration: __ITER },
  });

  const res = http.post(`${BASE_URL}/events`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  check(res, { 'status 202': (r) => r.status === 202 });

  // Kısa sleep: yüksek req/s (gerçek değer http_reqs /s ile ölçülür)
  sleep(Math.random() * 0.1 + 0.02);
}
