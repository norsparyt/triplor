-- Accounts and refresh tokens.
-- Column types are plain `text` on purpose: Hibernate's schema validation rejects char(n) and
-- citext against a String field.

CREATE TABLE users (
  id            uuid PRIMARY KEY,
  -- The application lower-cases emails before every read and write.
  -- The check catches any code path that forgets.
  email         text NOT NULL UNIQUE CHECK (email = lower(email)),
  password_hash text NOT NULL,
  role          text NOT NULL DEFAULT 'USER'   CHECK (role IN ('USER', 'ADMIN')),
  status        text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
  id          uuid PRIMARY KEY,
  user_id     uuid  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  -- Every token descended from one login shares a family. Reuse of a rotated token revokes the family.
  family_id   uuid  NOT NULL,
  -- SHA-256 of the opaque token. The token itself is never stored.
  token_hash  bytea NOT NULL UNIQUE,
  expires_at  timestamptz NOT NULL,
  revoked_at  timestamptz,
  replaced_by uuid,
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_user_idx   ON refresh_tokens (user_id);
