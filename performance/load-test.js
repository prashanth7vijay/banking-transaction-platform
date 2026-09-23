// Load test for the real Customer 360 endpoint: GET /api/v1/employee/customers/{id}/360
//
// Usage (see docs/customer360-performance.md "How to reproduce" for the full flow):
//   BASE_URL=http://localhost:8080 \
//   CUSTOMER_ID=<benchmark target customer id, from the seeder's log output> \
//   AUTH_TOKEN=<bearer token for an EMPLOYEE user, from POST /api/v1/auth/login> \
//   k6 run performance/load-test.js
//
// VUS controls concurrency; run it three times to get the 10/50/100-user
// comparison the spec asks for:
//   VUS=10  k6 run performance/load-test.js
//   VUS=50  k6 run performance/load-test.js
//   VUS=100 k6 run performance/load-test.js
//
// k6 prints requests/sec, p95, p99, and error rate (http_req_failed) in its
// end-of-run summary - those are the numbers to copy into the report, not
// anything computed by this script.

import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CUSTOMER_ID = __ENV.CUSTOMER_ID;
const AUTH_TOKEN = __ENV.AUTH_TOKEN;
const VUS = parseInt(__ENV.VUS || '10', 10);
const DURATION = __ENV.DURATION || '30s';

if (!CUSTOMER_ID) {
  throw new Error('Set CUSTOMER_ID to the benchmark target customer id (see Customer360DatasetSeeder log output)');
}
if (!AUTH_TOKEN) {
  throw new Error('Set AUTH_TOKEN to a valid EMPLOYEE bearer token (POST /api/v1/auth/login)');
}

export const options = {
  scenarios: {
    customer360_load: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
    },
  },
  thresholds: {
    // Informational only - the run's actual numbers go in the report either way.
    http_req_failed: ['rate<0.05'],
  },
};

export default function () {
  const res = http.get(`${BASE_URL}/api/v1/employee/customers/${CUSTOMER_ID}/360`, {
    headers: { Authorization: `Bearer ${AUTH_TOKEN}` },
  });
  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}