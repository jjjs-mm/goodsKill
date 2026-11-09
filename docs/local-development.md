# Local development baseline

This branch establishes a small, repeatable runtime for the ticketing-system
refactor. It keeps the original learning modules in the repository, while the
default Maven reactor and Docker stack contain only what the transaction path
needs.

## Prerequisites

- JDK 21
- Maven 3.9+
- Docker Desktop with Docker Compose v2

## Configure

Copy `.env.example` to `.env` and replace the local development passwords if
needed. `.env` is ignored by Git.

```bash
cp .env.example .env
```

Docker Compose reads the file through `--env-file`. Before starting Java
services from the terminal or IDE, export the same values into the current
shell so Spring can resolve the connection placeholders:

```bash
set -a
source .env
set +a
```

If the IDE does not inherit the terminal environment, import `.env` in its run
configuration instead.

## Start the core infrastructure

```bash
docker compose --env-file .env -f compose-core.yml up -d
docker compose --env-file .env -f compose-core.yml ps
```

The core stack contains MySQL, Redis, RabbitMQ, MongoDB and Nacos. MySQL
automatically loads SQL files from `goodskill-web/src/main/sql` on first
startup; MongoDB stores the asynchronously created ticket orders.

Optional observability:

```bash
docker compose --env-file .env \
  -f compose-core.yml \
  -f compose-observability.yml up -d
```

Optional experimental dependencies:

```bash
docker compose --env-file .env \
  -f compose-core.yml \
  -f compose-experimental.yml up -d
```

## Build

```bash
mvn clean verify
```

The default build intentionally excludes `goodskill-ai`, `goodskill-seata`,
`goodskill-job` and `goodskill-admin`. These are retained as reference modules
and can be built explicitly from their own `pom.xml` files.

## Nacos configuration

Open <http://localhost:18080/nacos> and create these Data IDs in the
`DEFAULT_GROUP` group:

- `goodskill-common-connection.yml`
- `goodskill-common.yml`

Use the files with the same names in the repository root as their contents.
Connection credentials use Spring property placeholders, so secrets remain in
environment variables instead of being committed to Git.

## Stop and reset

Stop containers while retaining data:

```bash
docker compose --env-file .env -f compose-core.yml down
```

To remove local database and Redis volumes, add `--volumes`. This permanently
deletes local development data.

## Git remotes

The recommended layout is:

```text
origin    https://github.com/jjjs-mm/goodsKill.git
upstream  https://github.com/techa03/goodsKill.git
```

Fetch upstream changes without mixing them into feature work:

```bash
git fetch upstream
git log --oneline main..upstream/main
```
