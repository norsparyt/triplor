-- A user's request to be marked as verified, reviewed by an admin.

CREATE TABLE verification_requests (
  id                uuid PRIMARY KEY,
  user_id           uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  -- Filled once media upload exists (Phase 3). Until then a request is just a record to review.
  document_media_id uuid,
  status            text NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
  reason            text CHECK (char_length(reason) <= 500),
  reviewed_by       uuid REFERENCES users (id),
  reviewed_at       timestamptz,
  created_at        timestamptz NOT NULL DEFAULT now()
);

-- At most one open request per user. A partial unique index enforces it even when two
-- submissions arrive at the same moment.
CREATE UNIQUE INDEX verification_one_pending_idx
  ON verification_requests (user_id) WHERE status = 'PENDING';

-- The admin review queue: oldest pending first.
CREATE INDEX verification_status_created_idx ON verification_requests (status, created_at);
