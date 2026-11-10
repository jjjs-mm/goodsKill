#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ORDER_JAR="$ROOT_DIR/goodskill-order-provider/goodskill-order-service/target/goodskill-order-service.jar"
TICKET_JAR="$ROOT_DIR/goodskill-seckill-provider/goodskill-service/target/goodskill-service.jar"

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
elif [[ -x /opt/homebrew/opt/openjdk@21/bin/java ]]; then
  JAVA_BIN=/opt/homebrew/opt/openjdk@21/bin/java
else
  JAVA_BIN="$(command -v java)"
fi

if [[ ! -f "$ORDER_JAR" || ! -f "$TICKET_JAR" ]]; then
  echo "Build the services first: mvn -pl goodskill-seckill-provider/goodskill-service,goodskill-order-provider/goodskill-order-service -am package -DskipTests"
  exit 1
fi

export SERVER_PORT="${ORDER_PORT:-19081}"
export SPRING_CLOUD_NACOS_DISCOVERY_ENABLED=false
export SPRING_CLOUD_NACOS_CONFIG_ENABLED=false
export SPRING_RABBITMQ_HOST="${RABBITMQ_HOST:-127.0.0.1}"
export SPRING_RABBITMQ_PORT="${RABBITMQ_PORT:-5672}"
export SPRING_RABBITMQ_USERNAME="${RABBITMQ_USERNAME:-guest}"
export SPRING_RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD:-guest}"
export SPRING_MONGODB_URI="${SPRING_MONGODB_URI:-mongodb://127.0.0.1:27017/goodskill_order}"
export MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=false

"$JAVA_BIN" -jar "$ORDER_JAR" &
ORDER_PID=$!

export SERVER_PORT="${TICKET_PORT:-54870}"
export MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
export MYSQL_PORT="${MYSQL_PORT:-3306}"
export MYSQL_DATABASE="${MYSQL_DATABASE:-seckill}"
export MYSQL_USERNAME="${MYSQL_USERNAME:-root}"
export MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-local-root-password}"
export REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
export REDIS_PORT="${REDIS_PORT:-6379}"
export REDIS_PASSWORD="${REDIS_PASSWORD:-local-redis-password}"
export SPRING_DATA_REDIS_HOST="$REDIS_HOST"
export SPRING_DATA_REDIS_PORT="$REDIS_PORT"
export SPRING_DATA_REDIS_PASSWORD="$REDIS_PASSWORD"
export DUBBO_ENABLED=false
export TICKET_ORDER_TIMEOUT="${TICKET_ORDER_TIMEOUT:-15s}"
export TICKET_RESERVATION_RETENTION="${TICKET_RESERVATION_RETENTION:-1m}"

"$JAVA_BIN" -Dloader.main=com.goodskill.ticketing.TicketingApplication \
  -cp "$TICKET_JAR" org.springframework.boot.loader.launch.PropertiesLauncher &
TICKET_PID=$!

cleanup() {
  kill "$TICKET_PID" "$ORDER_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM
wait "$TICKET_PID" "$ORDER_PID"
