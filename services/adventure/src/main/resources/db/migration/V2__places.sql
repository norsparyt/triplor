-- Reference data: cities from GeoNames (cities1000), loaded by infra/scripts/seed-places.sh.
CREATE TABLE places (
  id           bigint PRIMARY KEY,               -- GeoNames id
  name         text NOT NULL,
  ascii_name   text NOT NULL,
  admin1       text,                             -- state or province name, when known
  country_code text NOT NULL CHECK (char_length(country_code) = 2),
  lat          double precision NOT NULL CHECK (lat BETWEEN -90 AND 90),
  lng          double precision NOT NULL CHECK (lng BETWEEN -180 AND 180),
  population   bigint NOT NULL DEFAULT 0
);

-- Serves the type-ahead: lower(ascii_name) LIKE 'pref%'.
CREATE INDEX places_name_trgm_idx ON places USING gin (lower(ascii_name) gin_trgm_ops);
