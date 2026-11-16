COMPOSE := docker compose --env-file .env -f infra/compose/compose.yaml

.PHONY: help db-image up up-all down reset test keys places sample-places seed-adventures index-study smoke

help: ## List the available targets
	@grep -E "^[a-z-]+:.*##" $(MAKEFILE_LIST) | awk -F ":.*## " '{printf "  %-16s %s\n", $$1, $$2}'

db-image: ## Build the Postgres + PostGIS image
	docker build -t triplor/postgres:17 infra/docker/postgres

up: db-image ## Start the databases only and wait until they are healthy
	$(COMPOSE) up -d --wait

up-all: db-image ## Build the three services into images and start them with the databases
	$(COMPOSE) --profile app up -d --build --wait

down: ## Stop the local stack, keeping its data
	$(COMPOSE) --profile app down

reset: ## Stop the local stack and delete its data
	$(COMPOSE) --profile app down -v

test: db-image ## Build everything and run all tests (needs Docker)
	./mvnw -B verify

keys: ## Generate the development JWT signing key into .local/ (git-ignored)
	mkdir -p .local
	test -f .local/jwt-private.pem || openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out .local/jwt-private.pem
	chmod 600 .local/jwt-private.pem

places: ## Load the GeoNames cities into the adventure database (run the adventure service once first)
	infra/scripts/seed-places.sh

seed-adventures: ## Insert 100,000 random adventures for trying out search (remove: infra/scripts/seed-adventures.sh --delete)
	infra/scripts/seed-adventures.sh

index-study: ## Measure the search query under different index sets (development database only)
	infra/scripts/index-study.sh

sample-places: ## Load twenty sample cities, enough for the smoke test (the full set: make places)
	infra/scripts/seed-sample-places.sh

smoke: ## Run the end-to-end smoke test against the stack started by `make up-all`
	infra/scripts/smoke.sh
