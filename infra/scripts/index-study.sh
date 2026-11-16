#!/bin/sh
# Measures the discovery search under different sets of indexes with EXPLAIN (ANALYZE, BUFFERS).
# Seed the data first with infra/scripts/seed-adventures.sh. The results of one run are
# written up in docs/perf/phase-1-baseline.md.
#
# The query is the one DiscoveryRepository.search sends for a first page: open trips around
# Delhi that overlap a two-week window a month from now and have the style FOODIE or HIKING.
# It is run for a narrow radius (50 km) and a wide one (500 km).
#
# Indexes are dropped, and the styles GIN index that migration V5 removed is created again
# for comparison, inside a transaction that is rolled back, so the database is left exactly
# as it was. While that transaction is open the table is locked: run this against a
# development database only. Every statement runs three times; read the last plan, taken with
# the data already in memory.
set -eu

cd "$(dirname "$0")/../.."
PSQL="docker compose --env-file .env -f infra/compose/compose.yaml exec -T postgres psql -v ON_ERROR_STOP=1 --username adventure_app --dbname adventure"

$PSQL <<'SQL'
SELECT version();
SELECT count(*) AS adventures, pg_size_pretty(pg_table_size('adventures')) AS table_size FROM adventures;
SELECT indexrelname AS index, pg_size_pretty(pg_relation_size(indexrelid)) AS size
  FROM pg_stat_user_indexes WHERE relname = 'adventures' ORDER BY indexrelname;
SQL

study() {
  $PSQL -v radius="$1" <<'SQL'
\set lat 28.65195
\set lng 77.23149
\set matching 'FROM adventures a JOIN places p ON p.id = a.place_id CROSS JOIN (SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS origin) q WHERE a.status = ''OPEN'' AND a.organizer_id <> ''00000000-0000-0000-0000-000000000000'' AND a.start_date >= current_date - 1 AND ST_DWithin(a.location, q.origin, :radius) AND a.trip_dates && daterange(current_date + 30, current_date + 44, ''[]'') AND a.styles && ARRAY[''FOODIE'',''HIKING'']::text[]'
\set search 'SELECT a.id, a.organizer_id, a.start_date, a.end_date, a.styles, a.description, a.capacity, a.seats_taken, p.id AS place_id, p.name AS place_name, p.admin1, p.country_code, p.lat, p.lng, ST_Distance(a.location, q.origin) AS distance_m ' :matching ' ORDER BY a.start_date, a.id LIMIT 21'

\echo
\echo ########## Radius :radius m ##########
SELECT count(*) AS adventures_in_radius
  FROM adventures a
 WHERE ST_DWithin(a.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radius);
SELECT count(*) AS rows_matching_before_limit :matching;

\echo
\echo ===== 1. The schema as it is: location GiST and date-range GiST =====
EXPLAIN (ANALYZE, BUFFERS) :search;
EXPLAIN (ANALYZE, BUFFERS) :search;
EXPLAIN (ANALYZE, BUFFERS) :search;

BEGIN;
-- The index that migration V5 dropped, put back for comparison.
CREATE INDEX adventures_styles_gin ON adventures USING gin (styles);
\echo
\echo ===== 2. With the styles GIN index added back =====
EXPLAIN (ANALYZE, BUFFERS) :search;
EXPLAIN (ANALYZE, BUFFERS) :search;
EXPLAIN (ANALYZE, BUFFERS) :search;
DROP INDEX adventures_styles_gin;

DROP INDEX adventures_dates_gix;
\echo
\echo ===== 3. Location GiST only =====
EXPLAIN (ANALYZE, BUFFERS) :search;
EXPLAIN (ANALYZE, BUFFERS) :search;
EXPLAIN (ANALYZE, BUFFERS) :search;

DROP INDEX adventures_location_gix;
\echo
\echo ===== 4. No GiST index =====
EXPLAIN (ANALYZE, BUFFERS) :search;
EXPLAIN (ANALYZE, BUFFERS) :search;
EXPLAIN (ANALYZE, BUFFERS) :search;

-- The planner turns on JIT compilation for the expensive-looking sequential scan. At this
-- size compiling costs more than it saves, so the scan is measured without it as well.
SET LOCAL jit = off;
\echo
\echo ===== 5. No GiST index, JIT off =====
EXPLAIN (ANALYZE, BUFFERS) :search;
EXPLAIN (ANALYZE, BUFFERS) :search;
EXPLAIN (ANALYZE, BUFFERS) :search;
ROLLBACK;
SQL
}

study 50000
study 500000
