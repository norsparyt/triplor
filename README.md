# Triplor

Triplor is a travel-matching app. Travellers post an adventure (destination, dates, style, group size), others discover it and ask to join, and accepted travellers plan the trip together in chat.

The project is a Flutter client plus a set of Spring Boot services. The backend is being built in phases; the table below shows what exists today.

## Status

| Part | State |
|---|---|
| Flutter client (`mobile/`) | Adventure create, edit, list and delete against in-memory data |
| Identity service | Accounts, RS256 access tokens verifiable through a JWKS endpoint, rotating refresh tokens, profiles, and admin-reviewed verification |
| Adventure service | Place search, and adventure create, read, edit and cancel, with idempotent creates and ETag-based optimistic locking. Search by place, dates and style with keyset pagination, and scored matches. Join requests are next (Phase 1, in progress) |
| Chat service | Empty skeleton (Phase 1, in progress) |
| Kafka, Redis, notifications, gateway | Not started (Phase 2) |
| Flutter integration, admin console, GraphQL, trip planner | Not started (Phase 3) |
| CI/CD and AWS deployment | CI only; deployment not started (Phase 4) |

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

Start the local databases (PostgreSQL with PostGIS, and MongoDB). Copy `.env.example` to `.env` and set the passwords first.

```bash
make up
```

PostgreSQL is published on host port 5433, so it cannot collide with a PostgreSQL already installed on the machine.

Run the identity service. It needs a signing key, which `make keys` generates once into the git-ignored `.local/` folder.

```bash
make keys
```

```bash
./mvnw spring-boot:run -pl services/identity
```

Run the adventure service. It verifies tokens with identity's public key, so identity must be running too.

```bash
./mvnw spring-boot:run -pl services/adventure
```

Load the cities that adventures can be posted for. This downloads the GeoNames `cities1000` data set into the git-ignored `.local/` folder and imports it. Run it once, after the adventure service has started for the first time and created its tables.

```bash
make places
```

To have something to search, insert 100,000 random adventures. `infra/scripts/seed-adventures.sh --delete` removes them again.

```bash
make seed-adventures
```

The tests need the Postgres image and a running Docker daemon:

```bash
make test
```

Run the Flutter client:

```bash
cd mobile && flutter pub get && flutter run
```

`make help` lists the other targets.

## Data sources

Place names and coordinates come from [GeoNames](https://www.geonames.org), licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). The data is downloaded by `make places` and is not stored in this repository.
