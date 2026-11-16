CREATE TABLE join_requests (
  id           uuid PRIMARY KEY,
  adventure_id uuid NOT NULL REFERENCES adventures (id),
  requester_id uuid NOT NULL,                    -- a user in the identity service
  message      text CHECK (char_length(message) <= 300),
  status       text NOT NULL DEFAULT 'PENDING'
               CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN', 'EXPIRED')),
  expires_at   timestamptz NOT NULL,
  decided_at   timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now()
);

-- A traveller has at most one live request per adventure. Finished requests (rejected,
-- withdrawn, expired) are outside the index, so asking again later is allowed.
CREATE UNIQUE INDEX join_requests_one_active_idx
  ON join_requests (adventure_id, requester_id) WHERE status IN ('PENDING', 'ACCEPTED');
CREATE INDEX join_requests_adventure_idx ON join_requests (adventure_id, status, created_at);
CREATE INDEX join_requests_requester_idx ON join_requests (requester_id, created_at DESC);
-- For the job that expires old pending requests (Phase 2).
CREATE INDEX join_requests_expiry_idx    ON join_requests (expires_at) WHERE status = 'PENDING';

-- Who is on a trip. The organizer has a row too, with role ORGANIZER, and does not use a seat.
CREATE TABLE adventure_members (
  adventure_id uuid NOT NULL REFERENCES adventures (id),
  user_id      uuid NOT NULL,
  role         text NOT NULL CHECK (role IN ('ORGANIZER', 'MEMBER')),
  joined_at    timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (adventure_id, user_id)
);
CREATE INDEX adventure_members_user_idx ON adventure_members (user_id);

-- Adventures created before this migration get their organizer row now.
INSERT INTO adventure_members (adventure_id, user_id, role, joined_at)
SELECT id, organizer_id, 'ORGANIZER', created_at FROM adventures
ON CONFLICT DO NOTHING;
