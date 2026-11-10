# Ticket reservation performance report

Run date: 2026-11-10. This is a local engineering baseline, not a production
capacity promise.

## Environment and workload

- Apple Silicon (`arm64`), Docker Desktop 29.8.2 with about 8 GB assigned
- Java 21, Spring Boot 4.0.6, Redis 7.4, RabbitMQ 3.13, MongoDB 6.0, MySQL 8.0
- k6 2.3.0, local client and services on the same machine
- Constant arrival rate: 100 reservation requests/second for 30 seconds
- Unique user per request, quantity 1, dedicated session with 100,000 tickets
- Five-second payment timeout, so the run also exercised asynchronous order
  creation, timeout dead-lettering, cancellation and inventory compensation

Reproduce it with:

```bash
docker exec -i goodskill-mysql mysql -uroot -plocal-root-password seckill \
  < load-tests/prepare-ticket-session.sql
docker exec goodskill-redis redis-cli -a local-redis-password \
  DEL ticket:session:9000:stock ticket:session:9000:users
k6 run --summary-export=load-tests/k6-summary.json \
  load-tests/ticket-reservation.js
```

## Results

- Requests: 3,001 (100.02 requests/second)
- HTTP 202 and business acceptance: 100%
- Failed requests: 0 (0.00%)
- Response time: average 3.99 ms, median 2.52 ms
- Response time: p90 5.34 ms, p95 7.49 ms, maximum 137.64 ms
- Interrupted or dropped iterations: 0
- All configured thresholds passed (`p95 < 500 ms`, errors `< 1%`, accepted
  `> 99%`)

Post-run consistency checks were performed after timeout messages drained:

- MongoDB contained exactly 3,001 orders for session 9000; all were status 3
  (`PAYMENT_TIMEOUT`).
- Redis stock returned from 100,000 to 100,000, proving compensation completed
  for every accepted reservation.
- The create, timeout, compensation and dead-letter RabbitMQ queues all had
  zero ready and zero unacknowledged messages.

The machine-readable k6 output is committed as
`load-tests/k6-summary.json`.

## Interpretation and next step

At 100 RPS the system met the target with substantial latency headroom and no
consistency drift. This test establishes correctness under moderate concurrent
load. A production capacity test should run on isolated hosts, use production
resource limits and observability, step the arrival rate until saturation, and
report CPU, memory, Redis, RabbitMQ and MongoDB saturation alongside API
latency.
