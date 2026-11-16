#!/bin/sh
# Loads twenty sample cities into the adventure database: the same fixture the tests use.
# Enough for the smoke test and for trying the API by hand without the GeoNames download.
# For the full data set run `make places` instead; the two do not conflict.
#
# Run it after the adventure service has started once, so that Flyway has created the table.
set -eu

cd "$(dirname "$0")/../.."

docker compose --env-file .env -f infra/compose/compose.yaml exec -T postgres \
  psql -v ON_ERROR_STOP=1 --quiet --username adventure_app --dbname adventure \
  < services/adventure/src/test/resources/db/testdata/R__test_places.sql

echo "Sample places loaded."
