# PostgreSQL 17 with PostGIS.
# The public postgis/postgis image is amd64 only, so we build our own on top of the
# official multi-architecture postgres image. pgvector is added here in Phase 3.
FROM postgres:17-bookworm

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      postgresql-17-postgis-3 \
      postgresql-17-postgis-3-scripts \
 && rm -rf /var/lib/apt/lists/*
