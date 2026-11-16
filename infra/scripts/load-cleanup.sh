#!/bin/sh
# Deletes what the load tests created: trips whose description starts with "[load]", their
# join requests and members, the idempotency keys of the load-test users, and the trips'
# conversations in MongoDB.
#
#   infra/scripts/load-cleanup.sh            keep the load-test users
#   infra/scripts/load-cleanup.sh --users    also delete the loadtest-N@triplor.test users
set -eu

cd "$(dirname "$0")/../.."
COMPOSE="docker compose --env-file .env -f infra/compose/compose.yaml"
MONGO_PASSWORD="$(grep -E '^MONGO_ROOT_PASSWORD=' .env | head -n 1 | cut -d= -f2-)"

# MongoDB first, while PostgreSQL still knows which trips the load tests made. A long run
# leaves tens of thousands of ids, too many for a command line, so they travel in a file.
IDS_FILE="$(mktemp)"
trap 'rm -f "$IDS_FILE"' EXIT
$COMPOSE exec -T postgres psql -v ON_ERROR_STOP=1 -tA --username adventure_app --dbname adventure \
  -c "SELECT id FROM adventures WHERE description LIKE '[load]%'" > "$IDS_FILE"
$COMPOSE cp "$IDS_FILE" mongo:/tmp/load-adventure-ids.txt

$COMPOSE exec -T mongo mongosh --quiet -u root -p "$MONGO_PASSWORD" --authenticationDatabase admin chat --eval "
const adventureIds = require('fs').readFileSync('/tmp/load-adventure-ids.txt', 'utf8').split('\n').filter(Boolean);
let deleted = 0;
for (let i = 0; i < adventureIds.length; i += 1000) {
  const batch = adventureIds.slice(i, i + 1000);
  const ids = db.conversations.find({adventureId: {\$in: batch}}, {_id: 1}).toArray().map(c => c._id);
  db.messages.deleteMany({conversationId: {\$in: ids}});
  db.read_states.deleteMany({conversationId: {\$in: ids}});
  deleted += db.conversations.deleteMany({_id: {\$in: ids}}).deletedCount;
}
print('conversations deleted:', deleted);
"
$COMPOSE exec -T --user root mongo rm -f /tmp/load-adventure-ids.txt

$COMPOSE exec -T postgres psql -v ON_ERROR_STOP=1 --quiet --username adventure_app --dbname adventure <<'SQL'
BEGIN;
CREATE TEMP TABLE load_trips AS SELECT id FROM adventures WHERE description LIKE '[load]%';
DELETE FROM idempotency_keys WHERE idem_key LIKE 'load-%';
DELETE FROM join_requests WHERE adventure_id IN (SELECT id FROM load_trips);
DELETE FROM adventure_members WHERE adventure_id IN (SELECT id FROM load_trips);
DELETE FROM adventures WHERE id IN (SELECT id FROM load_trips);
SELECT count(*) AS trips_deleted FROM load_trips;
COMMIT;
VACUUM ANALYZE adventures;
SQL

if [ "${1:-}" = "--users" ]; then
  $COMPOSE exec -T postgres psql -v ON_ERROR_STOP=1 --quiet --username identity_app --dbname identity <<'SQL'
BEGIN;
CREATE TEMP TABLE load_users AS SELECT id FROM users WHERE email LIKE 'loadtest-%@triplor.test';
DELETE FROM verification_requests WHERE user_id IN (SELECT id FROM load_users);
DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM load_users);
DELETE FROM profiles WHERE user_id IN (SELECT id FROM load_users);
DELETE FROM users WHERE id IN (SELECT id FROM load_users);
SELECT count(*) AS users_deleted FROM load_users;
COMMIT;
SQL
fi
