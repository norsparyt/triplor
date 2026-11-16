#!/bin/sh
# Loads the GeoNames "cities1000" data set (every city with more than 1000 inhabitants, about
# 160,000 rows) into the adventure database's places table.
#
# Run it after the adventure service has started once, so that Flyway has created the table.
# It is safe to run again: existing rows are updated in place.
#
# The data is downloaded once into .local/geonames/ (git-ignored). Set GEONAMES_DIR to use
# files that are already on disk.
#
# Data: GeoNames (https://www.geonames.org), licensed under CC BY 4.0.
set -eu

cd "$(dirname "$0")/../.."

DATA_DIR="${GEONAMES_DIR:-.local/geonames}"
BASE_URL="https://download.geonames.org/export/dump"
COMPOSE="docker compose --env-file .env -f infra/compose/compose.yaml"

mkdir -p "$DATA_DIR"

if [ ! -f "$DATA_DIR/cities1000.txt" ]; then
  echo "Downloading cities1000.zip from GeoNames..."
  curl -fsSL -o "$DATA_DIR/cities1000.zip" "$BASE_URL/cities1000.zip"
  unzip -o -q "$DATA_DIR/cities1000.zip" -d "$DATA_DIR"
fi
if [ ! -f "$DATA_DIR/admin1CodesASCII.txt" ]; then
  echo "Downloading admin1CodesASCII.txt from GeoNames..."
  curl -fsSL -o "$DATA_DIR/admin1CodesASCII.txt" "$BASE_URL/admin1CodesASCII.txt"
fi

# psql's \copy reads files on the machine psql runs on, which is the container.
$COMPOSE cp "$DATA_DIR/cities1000.txt" postgres:/tmp/cities1000.txt
$COMPOSE cp "$DATA_DIR/admin1CodesASCII.txt" postgres:/tmp/admin1CodesASCII.txt

# The files are tab-separated with no quoting. CSV mode with a quote character that never
# occurs (backspace) reads them as they are; text mode would treat backslashes as escapes.
$COMPOSE exec -T postgres psql -v ON_ERROR_STOP=1 --username adventure_app --dbname adventure <<'SQL'
BEGIN;

CREATE TEMP TABLE geonames_staging (
  geonameid bigint, name text, asciiname text, alternatenames text,
  latitude double precision, longitude double precision,
  feature_class text, feature_code text, country_code text, cc2 text,
  admin1_code text, admin2_code text, admin3_code text, admin4_code text,
  population bigint, elevation text, dem text, timezone text, modification_date text
) ON COMMIT DROP;

-- Region names, keyed like "IN.24".
CREATE TEMP TABLE admin1_staging (
  code text, name text, asciiname text, geonameid bigint
) ON COMMIT DROP;

\copy geonames_staging FROM '/tmp/cities1000.txt' WITH (FORMAT csv, DELIMITER E'\t', QUOTE E'\b')
\copy admin1_staging FROM '/tmp/admin1CodesASCII.txt' WITH (FORMAT csv, DELIMITER E'\t', QUOTE E'\b')

INSERT INTO places (id, name, ascii_name, admin1, country_code, lat, lng, population)
SELECT g.geonameid, g.name, COALESCE(g.asciiname, g.name), a.name, g.country_code,
       g.latitude, g.longitude, COALESCE(g.population, 0)
  FROM geonames_staging g
  LEFT JOIN admin1_staging a ON a.code = g.country_code || '.' || g.admin1_code
 WHERE g.name IS NOT NULL
   AND g.country_code ~ '^[A-Z]{2}$'
ON CONFLICT (id) DO UPDATE
   SET name = EXCLUDED.name, ascii_name = EXCLUDED.ascii_name, admin1 = EXCLUDED.admin1,
       country_code = EXCLUDED.country_code, lat = EXCLUDED.lat, lng = EXCLUDED.lng,
       population = EXCLUDED.population;

COMMIT;

ANALYZE places;
SELECT count(*) AS places FROM places;
SQL

$COMPOSE exec -T postgres rm -f /tmp/cities1000.txt /tmp/admin1CodesASCII.txt
