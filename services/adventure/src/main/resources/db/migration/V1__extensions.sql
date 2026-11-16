-- PostGIS gives the geography type and distance queries; pg_trgm indexes the place-name search.
-- In Compose the init script has already created both as a superuser, so these are no-ops there.
-- In tests the container's user is a superuser, so they are created here.
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
