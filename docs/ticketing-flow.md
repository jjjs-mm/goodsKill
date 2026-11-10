# Ticket reservation transaction flow

The transaction path reuses the existing `seckill` table as the ticket-session
catalog. MySQL keeps session metadata and configured stock, Redis handles the
high-concurrency reservation ledger, and MongoDB stores orders.

## Flow

1. `POST /api/ticket-sessions/{sessionId}/reservations` validates the session
   window and lazily initializes Redis stock from MySQL.
2. `reserve_ticket.lua` atomically checks one-user-one-order, verifies stock,
   decrements it and records a reservation.
3. The inventory service publishes `TicketOrderCreateCommand` to RabbitMQ and
   immediately returns HTTP `202 Accepted` with a `reservationId`.
4. The order service consumes the command and inserts a pending MongoDB order.
   A unique sparse index on `reservationId` makes redelivery idempotent.
5. The order service puts a timeout command into a TTL queue. RabbitMQ
   dead-letters it to the timeout queue when the payment window ends.
6. The timeout consumer atomically changes only `PENDING_PAYMENT` orders to
   `CANCELLED`. A concurrent successful payment therefore prevents release.
7. The resulting compensation event reaches the inventory service, where
   `release_ticket.lua` restores stock and removes the user's reservation once.

If the initial order message cannot be published, the producer runs the same
compensation immediately. Order-consumer failures retry three times and are
then routed to `ticket.order.create.dead.queue` for inspection.

## Local exercise

Start infrastructure and export the local variables:

```bash
cp .env.example .env
docker compose --env-file .env -f compose-core.yml up -d
set -a
source .env
set +a
```

Build and start the two focused services. The ticketing entry point avoids
booting unrelated legacy Elasticsearch, Kafka and Dubbo components:

```bash
mvn -pl goodskill-seckill-provider/goodskill-service,goodskill-order-provider/goodskill-order-service \
  -am package -DskipTests
TICKET_ORDER_TIMEOUT=5s scripts/start-ticketing-services.sh
```

In a second terminal, run the repeatable HTTP verification:

```bash
scripts/verify-ticketing-e2e.sh
```

Inspect a session:

```bash
curl http://localhost:54870/api/ticket-sessions/1000
```

Reserve one ticket:

```bash
curl -i -X POST http://localhost:54870/api/ticket-sessions/1000/reservations \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: demo-user-1' \
  -d '{"userPhone":"13800138000","quantity":1}'
```

Use the returned reservation id to poll the asynchronously created order:

```bash
curl -H 'X-User-Id: demo-user-1' \
  http://localhost:19081/api/orders/by-reservation/{reservationId}
```

The seckill service currently uses port `54870`; the order service uses
`19081`. If Nacos overrides either port, use the value registered there.

## Redis keys

- `ticket:session:{sessionId}:stock`: available stock
- `ticket:session:{sessionId}:users`: user-to-reservation idempotency map
- `ticket:reservation:{reservationId}`: live reservation marker

The marker is retained longer than the payment timeout so that a delayed
compensation can still execute safely. Both reservation and release operations
are Lua scripts and therefore atomic inside Redis.

## Integration and load tests

The opt-in Testcontainers module starts isolated MySQL, Redis, RabbitMQ and
MongoDB containers and verifies reservation, duplicate prevention, message
routing, TTL dead-lettering, unique order keys, cancellation and idempotent
inventory release:

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn -Pintegration-tests \
  -pl goodskill-integration-tests -am verify
```

See [performance-report.md](performance-report.md) for the measured k6 baseline
and `load-tests/ticket-reservation.js` for the reproducible workload.
