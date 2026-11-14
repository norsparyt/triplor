#!/bin/sh
# Runs once, when the Postgres data volume is first created.
# Creates one database and one login role per service, so no service can read another's tables.
# Extensions are created here because PostGIS needs a superuser to install.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE ROLE identity_app  LOGIN PASSWORD '${IDENTITY_DB_PASSWORD}';
CREATE ROLE adventure_app LOGIN PASSWORD '${ADVENTURE_DB_PASSWORD}';
CREATE DATABASE identity  OWNER identity_app;
CREATE DATABASE adventure OWNER adventure_app;
SQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname adventure <<SQL
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
SQL
