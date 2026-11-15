package com.triplor.identity.verification;

import java.util.List;
import java.util.UUID;

import com.triplor.common.security.CurrentUser;
import com.triplor.identity.verification.VerificationDtos.RejectRequest;
import com.triplor.identity.verification.VerificationDtos.SubmitVerificationRequest;
import com.triplor.identity.verification.VerificationDtos.VerificationResponse;
import com.triplor.identity.verification.VerificationDtos.VerificationReviewItem;
import com.triplor.identity.verification.VerificationRequest.Status;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    /** Any signed-in user may ask to be verified. The body is optional. */
    @PostMapping("/users/me/verification")
    @ResponseStatus(HttpStatus.CREATED)
    VerificationResponse submit(CurrentUser user, @RequestBody(required = false) SubmitVerificationRequest request) {
        return verificationService.submit(user.id(), request == null ? null : request.documentMediaId());
    }

    // The admin endpoints below are guarded by role. @PreAuthorize is evaluated before the method
    // runs; a caller without ROLE_ADMIN gets 403 and the service is never reached.

    @GetMapping("/admin/verifications")
    @PreAuthorize("hasRole('ADMIN')")
    List<VerificationReviewItem> queue(
            @RequestParam(defaultValue = "PENDING") Status status,
            @RequestParam(defaultValue = "50") int limit) {
        return verificationService.list(status, limit);
    }

    @PostMapping("/admin/verifications/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    VerificationReviewItem approve(@PathVariable UUID id, CurrentUser admin) {
        return verificationService.approve(id, admin.id());
    }

    @PostMapping("/admin/verifications/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    VerificationReviewItem reject(@PathVariable UUID id, CurrentUser admin, @Valid @RequestBody RejectRequest request) {
        return verificationService.reject(id, admin.id(), request.reason());
    }
}
