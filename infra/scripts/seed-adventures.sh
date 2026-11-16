#!/bin/sh
# Inserts random open adventures into the adventure database, for trying out search by hand
# and for the index study (infra/scripts/index-study.sh).
#
#   infra/scripts/seed-adventures.sh [count]     default 100000
#   infra/scripts/seed-adventures.sh --delete    remove every seeded adventure again
#
# Each adventure is at one of the 5,000 most populous places, starts on a random day in the
# next 180 days, lasts 1 to 10 days and has one to three styles. Seeded rows are recognisable
# by a description that starts with "[seed]". Run `make places` first.
set -eu

cd "$(dirname "$0")/../.."
PSQL="docker compose --env-file .env -f infra/compose/compose.yaml exec -T postgres psql -v ON_ERROR_STOP=1 --username adventure_app --dbname adventure"

if [ "${1:-}" = "--delete" ]; then
  $PSQL -c "DELETE FROM join_requests WHERE adventure_id IN (SELECT id FROM adventures WHERE description LIKE '[seed]%')" \
        -c "DELETE FROM adventure_members WHERE adventure_id IN (SELECT id FROM adventures WHERE description LIKE '[seed]%')" \
        -c "DELETE FROM adventures WHERE description LIKE '[seed]%'" \
        -c "VACUUM ANALYZE adventures"
  exit 0
fi

COUNT="${1:-100000}"
case "$COUNT" in
  ''|*[!0-9]*) echo "count must be a number" >&2; exit 1 ;;
esac

$PSQL -v count="$COUNT" <<'SQL'
WITH cities AS (
  SELECT id, lat, lng, row_number() OVER (ORDER BY population DESC, id) AS rn
    FROM places
   ORDER BY population DESC, id
   LIMIT 5000
), total AS (
  SELECT count(*) AS n FROM cities
), picks AS (
  -- random() is evaluated once per generated row.
  SELECT g,
         1 + floor(random() * total.n)::int             AS city_rn,
         current_date + 1 + floor(random() * 180)::int  AS start_date,
         floor(random() * 10)::int                      AS extra_days,
         1 + floor(random() * 8)::int                   AS capacity
    FROM generate_series(1, :count) AS g, total
)
INSERT INTO adventures (id, organizer_id, place_id, lat, lng, start_date, end_date, styles, description, capacity)
SELECT gen_random_uuid(), gen_random_uuid(), c.id, c.lat, c.lng,
       k.start_date, k.start_date + k.extra_days,
       -- One to three styles, sorted as the application stores them. The reference to k.g
       -- makes PostgreSQL evaluate the subquery again for every row.
       ARRAY(SELECT s
               FROM (SELECT s
                       FROM unnest(ARRAY['ADVENTURE','BACKPACKING','RELAXING','FOODIE','CULTURE','HIKING']) AS s
                      WHERE k.g > 0
                      ORDER BY random()
                      LIMIT 1 + (k.g % 3)) AS chosen
              ORDER BY s),
       '[seed] Trip ' || k.g,
       k.capacity
  FROM picks k
  JOIN cities c ON c.rn = k.city_rn;

-- Every adventure has its organizer as a member.
INSERT INTO adventure_members (adventure_id, user_id, role)
SELECT id, organizer_id, 'ORGANIZER' FROM adventures WHERE description LIKE '[seed]%'
ON CONFLICT DO NOTHING;

VACUUM ANALYZE adventures;

SELECT count(*) AS seeded_adventures,
       count(DISTINCT place_id) AS places,
       min(start_date) AS first_start,
       max(start_date) AS last_start,
       round(avg(cardinality(styles)), 2) AS avg_styles
  FROM adventures
 WHERE description LIKE '[seed]%';
SQL
