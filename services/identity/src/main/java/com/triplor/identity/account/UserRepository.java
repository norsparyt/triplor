package com.triplor.identity.account;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Emails are stored lower-cased; pass an already normalised email. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
