package com.triplor.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;

import com.triplor.common.error.ApiException;
import com.triplor.common.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CurrentUserTest {

    @Test
    void aVerifiedUserPassesTheVerifiedCheck() {
        CurrentUser user = new CurrentUser(UUID.randomUUID(), Set.of("USER"), true);

        assertThatCode(user::requireVerified).doesNotThrowAnyException();
    }

    @Test
    void anUnverifiedUserIsRefusedWith403NotVerified() {
        CurrentUser user = new CurrentUser(UUID.randomUUID(), Set.of("USER"), false);

        assertThatThrownBy(user::requireVerified).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(e.code()).isEqualTo("NOT_VERIFIED");
        });
    }
}
