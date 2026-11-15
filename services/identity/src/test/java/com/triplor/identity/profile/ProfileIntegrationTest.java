package com.triplor.identity.profile;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.triplor.identity.IdentityIntegrationTest;
import com.triplor.identity.TestAccounts;
import com.triplor.identity.TestAccounts.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class ProfileIntegrationTest extends IdentityIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    TestAccounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new TestAccounts(mvc, jdbc);
    }

    @Test
    void aNewUserSeesTheirOwnProfile() throws Exception {
        Account asha = accounts.register("Asha");

        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, asha.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(asha.id().toString()))
                .andExpect(jsonPath("$.email").value(asha.email()))
                .andExpect(jsonPath("$.displayName").value("Asha"))
                .andExpect(jsonPath("$.styles", hasSize(0)))
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.verification").value("NONE"))
                .andExpect(jsonPath("$.memberSince").isNotEmpty());
    }

    @Test
    void profileEndpointsNeedAToken() throws Exception {
        mvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/users/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(get("/users").param("ids", UUID.randomUUID().toString())).andExpect(status().isUnauthorized());
    }

    @Test
    void patchChangesOnlyTheFieldsThatWereSent() throws Exception {
        Account asha = accounts.register("Asha");

        patchMe(asha, """
                {"bio": "Slow traveller. Coffee first.", "homeCity": "Pune", "styles": ["HIKING", "FOODIE"],
                 "dateOfBirth": "1996-04-12"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Asha"))
                .andExpect(jsonPath("$.bio").value("Slow traveller. Coffee first."))
                .andExpect(jsonPath("$.homeCity").value("Pune"))
                .andExpect(jsonPath("$.styles", contains("FOODIE", "HIKING")))
                .andExpect(jsonPath("$.dateOfBirth").value("1996-04-12"));

        // A second patch touching one field leaves the others as they were.
        patchMe(asha, """
                {"displayName": "Asha K"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Asha K"))
                .andExpect(jsonPath("$.bio").value("Slow traveller. Coffee first."))
                .andExpect(jsonPath("$.styles", hasSize(2)));

        // And the change was really written, not just echoed back.
        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, asha.bearer()))
                .andExpect(jsonPath("$.displayName").value("Asha K"))
                .andExpect(jsonPath("$.homeCity").value("Pune"));
    }

    @Test
    void anEmptyStringClearsBioAndHomeCity() throws Exception {
        Account asha = accounts.register("Asha");
        patchMe(asha, """
                {"bio": "Something", "homeCity": "Pune"}
                """).andExpect(status().isOk());

        patchMe(asha, """
                {"bio": "", "homeCity": "  "}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").doesNotExist())
                .andExpect(jsonPath("$.homeCity").doesNotExist());
    }

    @Test
    void invalidPatchesAreRejected() throws Exception {
        Account asha = accounts.register("Asha");

        patchMe(asha, "{\"displayName\": \"A\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        patchMe(asha, "{\"bio\": \"" + "x".repeat(501) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        patchMe(asha, "{\"styles\": [\"SKYDIVING\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        patchMe(asha, "{\"dateOfBirth\": \"" + today().plusDays(1) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void usersMustBeAdults() throws Exception {
        Account asha = accounts.register("Asha");

        patchMe(asha, "{\"dateOfBirth\": \"" + today().minusYears(17) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        patchMe(asha, "{\"dateOfBirth\": \"" + today().minusYears(18) + "\"}")
                .andExpect(status().isOk());
    }

    @Test
    void thePublicProfileShowsAgeButNeverEmailOrDateOfBirth() throws Exception {
        Account asha = accounts.register("Asha");
        Account ravi = accounts.register("Ravi");
        patchMe(asha, "{\"dateOfBirth\": \"" + today().minusYears(30).minusDays(1) + "\", \"styles\": [\"CULTURE\"]}")
                .andExpect(status().isOk());

        mvc.perform(get("/users/" + asha.id()).header(HttpHeaders.AUTHORIZATION, ravi.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(asha.id().toString()))
                .andExpect(jsonPath("$.displayName").value("Asha"))
                .andExpect(jsonPath("$.age").value(30))
                .andExpect(jsonPath("$.styles", contains("CULTURE")))
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.dateOfBirth").doesNotExist());
    }

    @Test
    void ageIsAbsentWhenNoDateOfBirthWasGiven() throws Exception {
        Account asha = accounts.register("Asha");

        mvc.perform(get("/users/" + asha.id()).header(HttpHeaders.AUTHORIZATION, asha.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.age").doesNotExist());
    }

    @Test
    void unknownAndSuspendedUsersAreNotFound() throws Exception {
        Account asha = accounts.register("Asha");
        Account suspended = accounts.register("Gone");
        jdbc.update("update users set status = 'SUSPENDED' where id = ?", suspended.id());

        mvc.perform(get("/users/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, asha.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/users/" + suspended.id()).header(HttpHeaders.AUTHORIZATION, asha.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void batchLookupReturnsTheUsersThatExist() throws Exception {
        Account asha = accounts.register("Asha");
        Account ravi = accounts.register("Ravi");
        String ids = asha.id() + "," + ravi.id() + "," + UUID.randomUUID() + "," + asha.id();

        mvc.perform(get("/users").param("ids", ids).header(HttpHeaders.AUTHORIZATION, asha.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].displayName", containsInAnyOrder("Asha", "Ravi")))
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    void batchLookupIsCappedAtOneHundredIds() throws Exception {
        Account asha = accounts.register("Asha");
        String tooMany = Stream.generate(() -> UUID.randomUUID().toString()).limit(101).collect(Collectors.joining(","));

        mvc.perform(get("/users").param("ids", tooMany).header(HttpHeaders.AUTHORIZATION, asha.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    /** The service works in UTC, so the tests must too, or they fail for a few hours around midnight. */
    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private ResultActions patchMe(Account account, String json) throws Exception {
        return mvc.perform(patch("/users/me")
                .header(HttpHeaders.AUTHORIZATION, account.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
