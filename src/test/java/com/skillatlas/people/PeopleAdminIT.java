package com.skillatlas.people;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.web.servlet.MockMvc;

import com.skillatlas.people.domain.Person;
import com.skillatlas.people.dto.PersonCreateRequest;
import com.skillatlas.people.enums.Role;
import com.skillatlas.security.JwtService;
import com.skillatlas.support.AbstractNeo4jIT;

// Admin CRUD over people (E2.1): who may write, what a soft delete leaves behind, and the two
// ways a create can be refused.
class PeopleAdminIT extends AbstractNeo4jIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    PeopleService peopleService;
    @Autowired
    PeopleRepository repository;
    @Autowired
    JwtService jwtService;
    @Autowired
    Neo4jClient neo4jClient;

    String suffix;
    String adminId;
    String memberId;
    String adminToken;
    String memberToken;
    String newEmail;

    @BeforeEach
    void seed() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        Person admin = peopleService.create(new PersonCreateRequest(
                "admin-" + suffix + "@test.com", "Password123!", "Site", "Admin", "Admin", null, Role.ADMIN));
        Person member = peopleService.create(new PersonCreateRequest(
                "member-" + suffix + "@test.com", "Password123!", "Mia", "Member", "Engineer", null, Role.MEMBER));
        adminId = admin.getId();
        memberId = member.getId();
        adminToken = jwtService.issue(adminId, Role.ADMIN);
        memberToken = jwtService.issue(memberId, Role.MEMBER);
        newEmail = "new-" + suffix + "@test.com";
    }

    // The suite runs against a real, possibly shared database — leave nothing behind.
    @AfterEach
    void cleanup() {
        neo4jClient.query("MATCH (p:Person) WHERE p.id IN $ids OR p.email IN $emails DETACH DELETE p")
                .bindAll(Map.of("ids", List.of(adminId, memberId), "emails", List.of(newEmail)))
                .run();
    }

    private String body(String email) {
        return """
                {"email":"%s","password":"Password123!","firstName":"Nina","lastName":"Hodzic",
                 "position":"Frontend Engineer","role":"MEMBER"}
                """.formatted(email);
    }

    @Test
    void create_asAdmin_returns201AndTheListShowsThePerson() throws Exception {
        mvc.perform(post("/api/v1/people")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON).content(body(newEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(newEmail))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                // The read shape never carries the hash, whatever the write accepted.
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(get("/api/v1/people").param("size", "100")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.email=='" + newEmail + "')]").exists());
    }

    @Test
    void create_asMember_isForbidden() throws Exception {
        mvc.perform(post("/api/v1/people")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(APPLICATION_JSON).content(body(newEmail)))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_withShortPassword_isRejected() throws Exception {
        mvc.perform(post("/api/v1/people")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"short","firstName":"Nina","lastName":"Hodzic","role":"MEMBER"}
                        """.formatted(newEmail)))
                .andExpect(status().isBadRequest());
    }

    // The unique constraint on Person.email does not know about isDeleted, so reusing the email of
    // a soft-deleted person has to be refused in Java — otherwise it surfaces as a 500.
    @Test
    void create_withEmailOfSoftDeletedPerson_returns409() throws Exception {
        peopleService.softDelete(memberId);

        mvc.perform(post("/api/v1/people")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON).content(body("member-" + suffix + "@test.com")))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_asAdmin_flagsThePersonButKeepsTheNode() throws Exception {
        mvc.perform(delete("/api/v1/people/{id}", memberId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        Optional<Boolean> deleted = neo4jClient
                .query("MATCH (p:Person {id: $id}) RETURN p.isDeleted AS deleted")
                .bind(memberId).to("id")
                .fetchAs(Boolean.class)
                .one();
        // A "soft" delete that removed the node would pass a list-based assertion just as well.
        Assertions.assertThat(deleted).contains(true);
        Assertions.assertThat(repository.findByIdAndDeletedFalse(memberId)).isEmpty();
    }

    @Test
    void delete_asMember_isForbidden() throws Exception {
        mvc.perform(delete("/api/v1/people/{id}", adminId)
                .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_self_isRefused() throws Exception {
        mvc.perform(delete("/api/v1/people/{id}", adminId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        Assertions.assertThat(repository.findByIdAndDeletedFalse(adminId)).isPresent();
    }
}
