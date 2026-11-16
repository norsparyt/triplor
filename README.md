# Triplor

Triplor is a travel-matching app. Travellers post an adventure (destination, dates, style, group size), others discover it and ask to join, and accepted travellers plan the trip together in chat.

The project is a Flutter client plus a set of Spring Boot services. The backend is being built in phases; the table below shows what exists today.

## Status

| Part | State |
|---|---|
| Flutter client (`mobile/`) | Adventure create, edit, list and delete against in-memory data |
| Identity service | Accounts, RS256 access tokens verifiable through a JWKS endpoint, rotating refresh tokens, profiles, and admin-reviewed verification |
| Adventure service | Place search; adventure create, read, edit and cancel with idempotent creates and ETag-based optimistic locking; search by place, dates and style with keyset pagination; scored matches; join requests with seat reservation that cannot oversell under concurrency |
| Chat service | One conversation per adventure in MongoDB: message history with keyset pagination, idempotent sending, read markers and unread counts. REST only; adventure adds members through a direct HTTP call that Phase 2 replaces with events |
| Kafka, Redis, notifications, gateway | Not started (Phase 2) |
| Flutter integration, admin console, GraphQL, trip planner | Not started (Phase 3) |
| CI/CD and AWS deployment | CI runs the tests and a smoke test against the Compose stack; deployment not started (Phase 4) |

## Documentation

- [Architecture and roadmap](docs/ARCHITECTURE.md): the target system, why each technology is there, and the build order.
- [Phase 1 plan](docs/plans/phase-1.md) and [Phase 2 plan](docs/plans/phase-2.md): milestone-by-milestone implementation plans.
- [Performance baseline](docs/perf/phase-1-baseline.md): measurements, starting with the index study that led to dropping one of the search indexes.

## Repository layout

```
mobile/          Flutter client
services/        identity, adventure, chat (Spring Boot)
libs/common/     cross-cutting code shared by the services
libs/test-support/  shared test helpers
infra/           Docker image for Postgres, Compose file, scripts
docs/            architecture and plans
```

## Prerequisites

- JDK 21
- Docker
- Flutter SDK, for `mobile/`

No global Maven install is needed; the wrapper (`./mvnw`) downloads it.

## Running

Copy `.env.example` to `.env` and set the values first. Generate the signing key once; it goes into the git-ignored `.local/` folder.

```bash
make keys
```

### The whole backend in containers

Build the three services into images and start them with PostgreSQL and MongoDB:

```bash
make up-all
```

Load twenty sample cities so that adventures can be created (or `make places` for the full GeoNames set):

```bash
make sample-places
```

Run the end-to-end smoke test. It registers two users, has the admin verify them, posts a trip, finds it by search, joins, accepts, and exchanges messages. It needs `TRIPLOR_ADMIN_EMAIL` and `TRIPLOR_ADMIN_PASSWORD` in `.env`.

```bash
make smoke
```

The services listen on 8081 (identity), 8082 (adventure) and 8083 (chat). Each serves its API description at `/v3/api-docs` and a Swagger UI at `/swagger-ui.html`, in this local stack only.

On Linux, the container user (uid 10001) must be able to read `.local/jwt-private.pem`.

### Services from the IDE or Maven

Start only the databases:

```bash
make up
```

PostgreSQL is published on host port 5433, so it cannot collide with a PostgreSQL already installed on the machine.

```bash
./mvnw spring-boot:run -pl services/identity
```

```bash
./mvnw spring-boot:run -pl services/chat
```

```bash
./mvnw spring-boot:run -pl services/adventure
```

Adventure verifies tokens with identity's public key and calls chat when a traveller is accepted, so start it last. Add `-Dspring-boot.run.profiles=local` for the Swagger UI.

### Data

`make places` downloads the GeoNames `cities1000` data set into `.local/` and imports it. Run it after the adventure service has started once and created its tables.

To have something to search, `make seed-adventures` inserts 100,000 random adventures; `infra/scripts/seed-adventures.sh --delete` removes them again.

### Tests

The tests need the Postgres image and a running Docker daemon:

```bash
make test
```

### Flutter client

```bash
cd mobile && flutter pub get && flutter run
```

`make help` lists every target.

## Data sources

Place names and coordinates come from [GeoNames](https://www.geonames.org), licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). The data is downloaded by `make places` and is not stored in this repository.
