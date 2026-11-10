import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const accepted = new Rate('ticket_reservation_accepted');
const rejected = new Counter('ticket_reservation_rejected');
const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:54870';
const sessionId = __ENV.SESSION_ID || '9000';
const targetRate = Number(__ENV.RATE || 100);
const duration = __ENV.DURATION || '30s';

export const options = {
  scenarios: {
    reservations: {
      executor: 'constant-arrival-rate',
      rate: targetRate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
    ticket_reservation_accepted: ['rate>0.99'],
  },
};

export default function () {
  const userId = `load-${__VU}-${__ITER}-${Date.now()}`;
  const response = http.post(
    `${baseUrl}/api/ticket-sessions/${sessionId}/reservations`,
    JSON.stringify({ userPhone: '13800138000', quantity: 1 }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-User-Id': userId,
      },
      tags: { endpoint: 'reserve-ticket' },
    },
  );

  const ok = check(response, {
    'HTTP 202': (r) => r.status === 202,
    'reservation accepted': (r) => r.json('status') === 'ACCEPTED',
    'reservation id returned': (r) => Boolean(r.json('reservationId')),
  });
  accepted.add(ok);
  if (!ok) {
    rejected.add(1);
  }
}
