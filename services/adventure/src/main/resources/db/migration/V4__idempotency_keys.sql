-- One row per (user, Idempotency-Key). Written in the same transaction as the business change,
-- so the key and its effect commit or roll back together.
CREATE TABLE idempotency_keys (
  user_id         uuid  NOT NULL,
  idem_key        text  NOT NULL CHECK (char_length(idem_key) BETWEEN 8 AND 128),
  operation       text  NOT NULL,
  request_hash    bytea NOT NULL,                -- SHA-256 of the operation and the canonical request
  response_status int,
  response_body   jsonb,
  created_at      timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, idem_key)
);

-- For the clean-up job that deletes old keys.
CREATE INDEX idempotency_keys_created_idx ON idempotency_keys (created_at);
