package com.skillatlas.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.skillatlas.people.PeopleService;
import com.skillatlas.people.domain.Person;
import com.skillatlas.people.dto.PersonCreateRequest;
import com.skillatlas.people.enums.Role;
import com.skillatlas.security.JwtService;
import com.skillatlas.support.AbstractNeo4jIT;

/** Spec E1.3: a member changes their own password, and can reach nobody else's. */
class AuthChangePasswordIT extends AbstractNeo4jIT {

    private static final String OLD_PASSWORD = "Password123!";
    private static final String NEW_PASSWORD = "Password456!";

    @Autowired
    MockMvc mvc;
    @Autowired
    PeopleService peopleService;
    @Autowired
    JwtService jwtService;
    @Autowired
    Neo4jClient neo4jClient;

    String memberId;
    String memberEmail;
    String memberToken;
    String otherId;
    String otherHashBefore;

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        memberEmail = "member-" + suffix + "@test.com";
        Person member = peopleService.create(new PersonCreateRequest(
                memberEmail, OLD_PASSWORD, "Mia", "Member", "Engineer", Role.MEMBER));
        Person other = peopleService.create(new PersonCreateRequest(
                "other-" + suffix + "@test.com", OLD_PASSWORD, "Omar", "Other", "Engineer", Role.MEMBER));
        memberId = member.getId();
        otherId = other.getId();
        memberToken = jwtService.issue(memberId, Role.MEMBER);
        otherHashBefore = hashOf(otherId);
    }

    @AfterEach
    void cleanup() {
        neo4jClient.query("MATCH (p:Person) WHERE p.id IN $ids DETACH DELETE p")
                .bindAll(Map.of("ids", List.of(memberId, otherId)))
                .run();
    }

    private ResultActions changePassword(String current, String next) throws Exception {
        return mvc.perform(post("/api/v1/auth/change-password")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)));
    }

    private ResultActions login(String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(memberEmail, password)));
    }

    private String hashOf(String personId) {
        return neo4jClient.query("MATCH (p:Person {id: $id}) RETURN p.passwordHash AS h")
                .bindAll(Map.of("id", personId))
                .fetchAs(String.class)
                .mappedBy((t, r) -> r.get("h").asString(null))
                .one()
                .orElse(null);
    }

    @Test
    void changePassword_retiresTheOldPasswordAndAcceptsTheNewOne() throws Exception {
        changePassword(OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        login(OLD_PASSWORD).andExpect(status().isUnauthorized());
        login(NEW_PASSWORD).andExpect(status().isOk());
    }

    // 400, not 401: a typo here must not read as "your session expired" and sign the caller out.
    @Test
    void changePassword_withTheWrongCurrentPassword_is400AndChangesNothing() throws Exception {
        changePassword("NotMyPassword!", NEW_PASSWORD).andExpect(status().isBadRequest());

        login(OLD_PASSWORD).andExpect(status().isOk());
        login(NEW_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_withATooShortNewPassword_is400AndChangesNothing() throws Exception {
        changePassword(OLD_PASSWORD, "short7!").andExpect(status().isBadRequest());

        login(OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void changePassword_toTheSamePassword_is400() throws Exception {
        changePassword(OLD_PASSWORD, OLD_PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_withoutAToken_is401() throws Exception {
        mvc.perform(post("/api/v1/auth/change-password")
                .contentType(APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(OLD_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());

        login(OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void changePassword_storesABcryptHashAndLeavesEveryoneElseAlone() throws Exception {
        changePassword(OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(hashOf(memberId)).isNotNull().startsWith("$2").isNotEqualTo(NEW_PASSWORD);
        assertThat(hashOf(otherId)).isEqualTo(otherHashBefore);
    }
}
