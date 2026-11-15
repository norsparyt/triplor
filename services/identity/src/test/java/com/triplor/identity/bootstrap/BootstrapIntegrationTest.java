package com.triplor.identity.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.triplor.identity.IdentityIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The start-up account creators, run by hand against the real database. They are constructed
 * directly with test settings, because the test context has neither an admin configured nor the
 * seed profile active.
 */
class BootstrapIntegrationTest extends IdentityIntegrationTest {

    @Autowired
    AccountProvisioner provisioner;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtDecoder jwtDecoder;

    @Test
    void nothingIsCreatedWhenNoAdminIsConfigured() {
        int before = userCount();

        new AdminBootstrap(new BootstrapProperties(null, null, null, null), provisioner, passwordEncoder).run(null);
        new AdminBootstrap(new BootstrapProperties("admin@example.com", " ", null, null), provisioner, passwordEncoder).run(null);

        assertThat(userCount()).isEqualTo(before);
    }

    @Test
    void theConfiguredAdminIsCreatedOnceAndCanLogIn() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        String password = "a long admin password";
        AdminBootstrap bootstrap =
                new AdminBootstrap(new BootstrapProperties(email, password, null, null), provisioner, passwordEncoder);

        bootstrap.run(null);
        bootstrap.run(null); // a second start must not fail or duplicate

        assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email)).isEqualTo(1);

        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.verified").value(true))
                .andReturn().getResponse().getContentAsString();
        Jwt jwt = jwtDecoder.decode(JsonPath.read(body, "$.accessToken"));
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER", "ADMIN");
    }

    @Test
    void bootstrapNeverChangesAnExistingAccount() {
        String email = "existing-" + UUID.randomUUID() + "@example.com";
        provisioner.createIfAbsent(email, passwordEncoder.encode("the original password"), com.triplor.identity.account.User.Role.USER, "Existing", false);

        new AdminBootstrap(new BootstrapProperties(email, "a different password", null, null), provisioner, passwordEncoder).run(null);

        assertThat(jdbc.queryForObject("select role from users where email = ?", String.class, email)).isEqualTo("USER");
        String hash = jdbc.queryForObject("select password_hash from users where email = ?", String.class, email);
        assertThat(passwordEncoder.matches("the original password", hash)).isTrue();
    }

    @Test
    void seederCreatesVerifiedUsersAndIsSafeToRunTwice() throws Exception {
        String password = "load test password";
        LoadTestUserSeeder seeder =
                new LoadTestUserSeeder(new BootstrapProperties(null, null, 5, password), provisioner, passwordEncoder);

        seeder.run(null);
        seeder.run(null);

        Integer seeded = jdbc.queryForObject("""
                select count(*) from users u join profiles p on p.user_id = u.id
                 where u.email like 'loadtest-%@triplor.test' and p.verified_at is not null
                """, Integer.class);
        assertThat(seeded).isEqualTo(5);

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + LoadTestUserSeeder.emailOf(3) + "\", \"password\": \"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.verified").value(true));
    }

    @Test
    void seederRefusesToRunWithoutAPassword() {
        LoadTestUserSeeder seeder =
                new LoadTestUserSeeder(new BootstrapProperties(null, null, 5, ""), provisioner, passwordEncoder);

        assertThatThrownBy(() -> seeder.run(null)).isInstanceOf(IllegalStateException.class);
    }

    private int userCount() {
        return jdbc.queryForObject("select count(*) from users", Integer.class);
    }
}
