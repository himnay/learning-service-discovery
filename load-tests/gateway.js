// k6 load test against gateway-service (:8080), which routes via Eureka (lb://) to the backends.
// Run:   k6 run load-tests/gateway.js                      (smoke: 1 VU, 30 s)
//        k6 run -e PROFILE=load load-tests/gateway.js      (ramp to 20 VUs over ~2 min)
//        k6 run -e PROFILE=stress load-tests/gateway.js    (ramp to 100 VUs)
// Env:   BASE_URL (default http://localhost:8080), ACTUATOR_USER / ACTUATOR_PASSWORD (default admin/admin)
import http from 'k6/http';
import encoding from 'k6/encoding';
import {check, group, sleep} from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USER = __ENV.ACTUATOR_USER || 'admin';
const PASSWORD = __ENV.ACTUATOR_PASSWORD || 'admin';

// the gateway is behind HTTP Basic, same shared user as every module
const params = {
    headers: {Authorization: `Basic ${encoding.b64encode(`${USER}:${PASSWORD}`)}`},
};

const profiles = {
    smoke: {vus: 1, duration: '30s'},
    load: {
        stages: [
            {duration: '30s', target: 20},
            {duration: '1m', target: 20},
            {duration: '30s', target: 0},
        ],
    },
    stress: {
        stages: [
            {duration: '1m', target: 50},
            {duration: '1m', target: 100},
            {duration: '1m', target: 100},
            {duration: '30s', target: 0},
        ],
    },
};

export const options = {
    ...profiles[__ENV.PROFILE || 'smoke'],
    thresholds: {
        http_req_failed: ['rate<0.01'],      // <1% errors
        http_req_duration: ['p(95)<500'],    // 95% of requests under 500 ms
        checks: ['rate>0.99'],
    },
};

// sample data seeded in the services' Postgres databases
const USER_IDS = [1, 2, 3, 4, 5];
const ORDER_IDS = [101, 102, 103, 104, 105, 106];

const pick = (list) => list[Math.floor(Math.random() * list.length)];

export default function () {
    group('user-service', () => {
        const res = http.get(`${BASE_URL}/api/users`, {...params, tags: {name: 'GET /api/users'}});
        check(res, {'users 200': (r) => r.status === 200});
        const one = http.get(`${BASE_URL}/api/users/${pick(USER_IDS)}`, {
            ...params,
            tags: {name: 'GET /api/users/{id}'}
        });
        check(one, {'user 200': (r) => r.status === 200});
    });

    group('product-service', () => {
        const res = http.get(`${BASE_URL}/api/products`, {...params, tags: {name: 'GET /api/products'}});
        check(res, {'products 200': (r) => r.status === 200});
    });

    group('order-service', () => {
        const res = http.get(`${BASE_URL}/api/orders`, {...params, tags: {name: 'GET /api/orders'}});
        check(res, {'orders 200': (r) => r.status === 200});
        // order-service -> user-service call, load-balanced via Eureka without the gateway
        const withUser = http.get(`${BASE_URL}/api/orders/${pick(ORDER_IDS)}/with-user`, {
            ...params,
            tags: {name: 'GET /api/orders/{id}/with-user'},
        });
        check(withUser, {'order with user 200': (r) => r.status === 200});
    });

    sleep(1);
}
