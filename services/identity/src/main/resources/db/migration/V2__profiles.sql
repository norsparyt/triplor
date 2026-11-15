-- Public profile, one row per user, created at registration.
-- The profile endpoints and the verification workflow arrive in milestone M2.

CREATE TABLE profiles (
  user_id         uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
  display_name    text NOT NULL CHECK (char_length(display_name) BETWEEN 2 AND 50),
  date_of_birth   date,
  bio             text CHECK (char_length(bio) <= 500),
  avatar_media_id uuid,
  home_city       text,
  styles          text[] NOT NULL DEFAULT '{}',
  -- Set when an admin approves a verification request. Null means not verified.
  verified_at     timestamptz,
  updated_at      timestamptz NOT NULL DEFAULT now()
);
