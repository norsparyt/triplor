package com.triplor.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Creates accounts through the real API, so integration tests start from a realistic state. */
public final class TestAccounts {

    public static final String PASSWORD = "correct horse battery";

    public record Account(UUID id, String email, String accessToken, String refreshToken) {

        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    private final MockMvc mvc;
    private final JdbcTemplate jdbc;

    public TestAccounts(MockMvc mvc, JdbcTemplate jdbc) {
        this.mvc = mvc;
        this.jdbc = jdbc;
    }

    public Account register(String displayName) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String body = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s", "displayName": "%s"}
                        """.formatted(email, PASSWORD, displayName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return toAccount(body);
    }

    /** Logs in again, which issues a fresh access token reflecting the user's current role and verified state. */
    public Account login(String email) throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return toAccount(body);
    }

    /** There is no API to create an admin, by design, so the role is set directly in the database. */
    public Account registerAdmin() throws Exception {
        Account account = register("Admin");
        jdbc.update("update users set role = 'ADMIN' where id = ?", account.id());
        return login(account.email());
    }

    private static Account toAccount(String body) {
        return new Account(
                UUID.fromString(JsonPath.read(body, "$.user.id")),
                JsonPath.read(body, "$.user.email"),
                JsonPath.read(body, "$.accessToken"),
                JsonPath.read(body, "$.refreshToken"));
    }
}
