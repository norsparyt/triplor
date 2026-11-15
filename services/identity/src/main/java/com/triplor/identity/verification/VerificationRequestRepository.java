package com.triplor.identity.verification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.triplor.identity.verification.VerificationRequest.Status;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationRequestRepository extends JpaRepository<VerificationRequest, UUID> {

    boolean existsByUserIdAndStatus(UUID userId, Status status);

    /** The user's most recent request, whatever its status. */
    Optional<VerificationRequest> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    /** The review queue, oldest first. */
    List<VerificationRequest> findByStatusOrderByCreatedAtAsc(Status status, Pageable page);

    /** Locks the row, so two admins reviewing the same request are processed one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VerificationRequest v where v.id = :id")
    Optional<VerificationRequest> findByIdForUpdate(@Param("id") UUID id);
}
