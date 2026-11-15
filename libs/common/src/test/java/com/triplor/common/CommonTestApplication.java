package com.triplor.common;

import java.util.Map;

import com.triplor.common.error.ApiException;
import com.triplor.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** A tiny application that exists only so the shared auto-configuration can be tested end to end. */
@SpringBootApplication
public class CommonTestApplication {

    record EchoRequest(@NotBlank @Size(max = 5) String name) {
    }

    @RestController
    static class ProbeController {

        @GetMapping("/open/ping")
        Map<String, String> ping() {
            return Map.of("status", "ok");
        }

        @GetMapping("/me")
        CurrentUser me(CurrentUser user) {
            return user;
        }

        @GetMapping("/admin/only")
        @PreAuthorize("hasRole('ADMIN')")
        Map<String, String> adminOnly() {
            return Map.of("status", "ok");
        }

        @PostMapping("/open/echo")
        EchoRequest echo(@Valid @RequestBody EchoRequest request) {
            return request;
        }

        @GetMapping("/open/teapot")
        void teapot() {
            throw new ApiException(HttpStatus.I_AM_A_TEAPOT, "TEAPOT", "I am a teapot");
        }

        @GetMapping("/open/boom")
        void boom() {
            throw new IllegalStateException("secret internal detail");
        }
    }
}
