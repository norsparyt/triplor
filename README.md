# Triplor

Triplor is a travel-matching app. Travellers post an adventure (destination, dates, style, group size), others discover it and ask to join, and accepted travellers plan the trip together in chat.

The project is a Flutter client plus a set of Spring Boot services. The backend is being built in phases; the table below shows what exists today.

## Status

| Part | State |
|---|---|
| Flutter client (`mobile/`) | Adventure create, edit, list and delete against in-memory data |
| Backend skeleton | Maven monorepo with three empty services that build and start |
| Identity, adventure and chat services | Not started (Phase 1) |
| Kafka, Redis, notifications, gateway | Not started (Phase 2) |
| Flutter integration, admin console, GraphQL, trip planner | Not started (Phase 3) |
| CI/CD and AWS deployment | CI only; deployment not started (Phase 4) |

## Documentation

- [Architecture and roadmap](docs/ARCHITECTURE.md): the target system, why each technology is there, and the build order.
- [Phase 1 plan](docs/plans/phase-1.md) and [Phase 2 plan](docs/plans/phase-2.md): milestone-by-milestone implementation plans.

## Repository layout

```
mobile/          Flutter client
services/        identity, adventure, chat (Spring Boot)
libs/common/     cross-cutting code shared by the services
libs/test-support/  shared test helpers
infra/           Docker image for Postgres, Compose file
docs/            architecture and plans
```

## Prerequisites

- JDK 21
- Docker
- Flutter SDK, for `mobile/`

No global Maven install is needed; the wrapper (`./mvnw`) downloads it.

## Running

Build and test the backend:

```bash
./mvnw verify
```

Start the local databases (PostgreSQL with PostGIS, and MongoDB). Copy `.env.example` to `.env` and set the passwords first.

```bash
make up
```

Run the Flutter client:

```bash
cd mobile && flutter pub get && flutter run
```

`make help` lists the other targets.
