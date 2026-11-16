COMPOSE := docker compose --env-file .env -f infra/compose/compose.yaml

.PHONY: help db-image up down reset test keys places

help: ## List the available targets
	@grep -E "^[a-z-]+:.*##" $(MAKEFILE_LIST) | awk -F ":.*## " '{printf "  %-10s %s\n", $$1, $$2}'

db-image: ## Build the Postgres + PostGIS image
	docker build -t triplor/postgres:17 infra/docker/postgres

up: db-image ## Start the local stack and wait until it is healthy
	$(COMPOSE) up -d --wait

down: ## Stop the local stack, keeping its data
	$(COMPOSE) down

reset: ## Stop the local stack and delete its data
	$(COMPOSE) down -v

test: db-image ## Build everything and run all tests (needs Docker)
	./mvnw -B verify

keys: ## Generate the development JWT signing key into .local/ (git-ignored)
	mkdir -p .local
	test -f .local/jwt-private.pem || openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out .local/jwt-private.pem
	chmod 600 .local/jwt-private.pem

places: ## Load the GeoNames cities into the adventure database (run the adventure service once first)
	infra/scripts/seed-places.sh
