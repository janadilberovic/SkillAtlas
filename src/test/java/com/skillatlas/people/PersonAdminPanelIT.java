package com.skillatlas.people;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.skillatlas.people.domain.Person;
import com.skillatlas.people.dto.PersonCreateRequest;
import com.skillatlas.people.enums.Role;
import com.skillatlas.security.JwtService;
import com.skillatlas.support.AbstractNeo4jIT;

/**
 * The admin panel on a profile: giving an imported person a password, and the one profile field
 * that exists only to feed that panel.
 */
class PersonAdminPanelIT extends AbstractNeo4jIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    PeopleService peopleService;
    @Autowired
    JwtService jwtService;
    @Autowired
    Neo4jClient neo4jClient;

    String suffix;
    String adminId;
    String memberId;
    String importedId;
    String importedEmail;
    String adminToken;
    String memberToken;

    @BeforeEach
    void seed() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        Person admin = peopleService.create(new PersonCreateRequest(
                "admin-" + suffix + "@test.com", "Password123!", "Site", "Admin", "Admin", Role.ADMIN));
        Person member = peopleService.create(new PersonCreateRequest(
                "member-" + suffix + "@test.com", "Password123!", "Mia", "Member", "Engineer", Role.MEMBER));
        importedEmail = "nina-" + suffix + "@test.com";
        Person imported = peopleService.create(new PersonCreateRequest(
                importedEmail, "Password123!", "Nina", "Hodzic", "Frontend Engineer", Role.MEMBER));
        adminId = admin.getId();
        memberId = member.getId();
        importedId = imported.getId();
        // What E3 actually leaves behind: a person with no hash at all, who cannot sign in yet.
        neo4jClient.query("MATCH (p:Person {id: $id}) REMOVE p.passwordHash")
                .bindAll(Map.of("id", importedId))
                .run();
        adminToken = jwtService.issue(adminId, Role.ADMIN);
        memberToken = jwtService.issue(memberId, Role.MEMBER);
    }

    // The suite runs against a real, possibly shared database — leave nothing behind.
    @AfterEach
    void cleanup() {
        neo4jClient.query("MATCH (p:Person) WHERE p.id IN $ids DETACH DELETE p")
                .bindAll(Map.of("ids", List.of(adminId, memberId, importedId)))
                .run();
    }

    private ResultActions login(String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(importedEmail, password)));
    }

    private String passwordBody(String password) {
        return "{\"password\":\"%s\"}".formatted(password);
    }

    @Test
    void setPassword_asAdmin_turnsAnImportedPersonIntoOneWhoCanSignIn() throws Exception {
        login("Welcome123!").andExpect(status().isUnauthorized());

        mvc.perform(put("/api/v1/people/{id}/password", importedId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON).content(passwordBody("Welcome123!")))
                .andExpect(status().isNoContent());

        login("Welcome123!").andExpect(status().isOk());
    }

    @Test
    void setPassword_isNeverStoredInPlain() throws Exception {
        mvc.perform(put("/api/v1/people/{id}/password", importedId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON).content(passwordBody("Welcome123!")))
                .andExpect(status().isNoContent());

        String hash = neo4jClient.query("MATCH (p:Person {id: $id}) RETURN p.passwordHash AS h")
                .bindAll(Map.of("id", importedId))
                .fetchAs(String.class)
                .mappedBy((t, r) -> r.get("h").asString(null))
                .one()
                .orElse(null);
        assertThat(hash).isNotNull().startsWith("$2");
    }

    @Test
    void setPassword_tooShort_isRejectedAndChangesNothing() throws Exception {
        mvc.perform(put("/api/v1/people/{id}/password", importedId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON).content(passwordBody("short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").exists());

        login("short").andExpect(status().isUnauthorized());
    }

    // Admin-only: a member cannot set anyone's password, their own included. Self-service
    // change-password is a separate endpoint that does not exist yet.
    @Test
    void setPassword_asMember_isForbidden() throws Exception {
        mvc.perform(put("/api/v1/people/{id}/password", memberId)
                .header("Authorization", "Bearer " + memberToken)
                .contentType(APPLICATION_JSON).content(passwordBody("Welcome123!")))
                .andExpect(status().isForbidden());
    }

    @Test
    void setPassword_forUnknownPerson_returns404() throws Exception {
        mvc.perform(put("/api/v1/people/{id}/password", "no-such-person")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON).content(passwordBody("Welcome123!")))
                .andExpect(status().isNotFound());
    }

    @Test
    void profile_tellsAnAdminWhetherThePersonCanSignInAtAll() throws Exception {
        mvc.perform(get("/api/v1/people/{id}", importedId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword").value(false));

        mvc.perform(get("/api/v1/people/{id}", memberId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.hasPassword").value(true));
    }

    // The panel is admin-only, and so is the field that feeds it — including on your own profile.
    @Test
    void profile_hidesHasPasswordFromEveryoneElse() throws Exception {
        mvc.perform(get("/api/v1/people/{id}", importedId)
                .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword").value(nullValue()));

        mvc.perform(get("/api/v1/people/{id}", memberId)
                .header("Authorization", "Bearer " + memberToken))
                .andExpect(jsonPath("$.hasPassword").value(nullValue()));
    }

    @Test
    void profile_neverCarriesTheHashItself() throws Exception {
        mvc.perform(get("/api/v1/people/{id}", memberId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
}
