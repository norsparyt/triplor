CREATE TABLE adventures (
  id           uuid PRIMARY KEY,
  organizer_id uuid NOT NULL,                    -- a user in the identity service; no foreign key across services
  place_id     bigint NOT NULL REFERENCES places (id),
  -- Copied from the place, so the generated column below can be computed from this row alone.
  lat          double precision NOT NULL,
  lng          double precision NOT NULL,
  location     geography(Point, 4326)
               GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography) STORED,
  start_date   date NOT NULL,
  end_date     date NOT NULL,
  trip_dates   daterange GENERATED ALWAYS AS (daterange(start_date, end_date, '[]')) STORED,
  styles       text[] NOT NULL,
  description  text NOT NULL CHECK (char_length(description) BETWEEN 1 AND 300),
  -- Travellers who can join, not counting the organizer.
  capacity     int  NOT NULL CHECK (capacity BETWEEN 1 AND 20),
  seats_taken  int  NOT NULL DEFAULT 0,
  status       text NOT NULL DEFAULT 'OPEN'
               CHECK (status IN ('OPEN', 'FULL', 'CANCELLED', 'COMPLETED')),
  -- Incremented by every edit; sent to clients as the ETag.
  version      bigint NOT NULL DEFAULT 0,
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT adventures_dates_chk  CHECK (end_date >= start_date),
  -- The backstop: whatever the application does, the database refuses to oversell a trip.
  CONSTRAINT adventures_seats_chk  CHECK (seats_taken BETWEEN 0 AND capacity),
  CONSTRAINT adventures_styles_chk CHECK (
    cardinality(styles) BETWEEN 1 AND 6
    AND styles <@ ARRAY['ADVENTURE','BACKPACKING','RELAXING','FOODIE','CULTURE','HIKING']::text[])
);

CREATE INDEX adventures_location_gix  ON adventures USING gist (location);
CREATE INDEX adventures_dates_gix     ON adventures USING gist (trip_dates);
CREATE INDEX adventures_styles_gin    ON adventures USING gin (styles);
CREATE INDEX adventures_organizer_idx ON adventures (organizer_id, start_date DESC);
CREATE INDEX adventures_open_feed_idx ON adventures (start_date, id) WHERE status = 'OPEN';
