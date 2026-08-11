package com.skillatlas.people;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.skillatlas.people.domain.Person;
import com.skillatlas.people.dto.PersonCreateRequest;
import com.skillatlas.people.enums.Role;
import com.skillatlas.security.JwtService;
import com.skillatlas.skills.SkillsService;
import com.skillatlas.skills.domain.Skill;
import com.skillatlas.skills.dto.SkillCreateRequest;
import com.skillatlas.skills.enums.SkillCategory;
import com.skillatlas.support.AbstractNeo4jIT;

// Integration tests for "My Skills": IDOR guard + happy path + rules, against a real Neo4j.
class PeopleSkillsIT extends AbstractNeo4jIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    PeopleService peopleService;
    @Autowired
    SkillsService skillsService;
    @Autowired
    JwtService jwtService;
    @Autowired
    Neo4jClient neo4jClient;

    String adaId;
    String bobId;
    String skillId;
    String adaToken;

    @BeforeEach
    void seed() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        Person ada = peopleService.create(new PersonCreateRequest(
                "ada-" + u + "@test.com", "Password123!", "Ada", "Lovelace", "Engineer", null, Role.MEMBER));
        Person bob = peopleService.create(new PersonCreateRequest(
                "bob-" + u + "@test.com", "Password123!", "Bob", "Byte", "Engineer", null, Role.MEMBER));
        Skill skill = skillsService.create(new SkillCreateRequest("Neo4j-" + u, SkillCategory.DATABASE, "#4581C3"));
        adaId = ada.getId();
        bobId = bob.getId();
        skillId = skill.getId();
        adaToken = jwtService.issue(adaId, Role.MEMBER);
    }

    // The suite runs against a real, possibly shared database — leave nothing behind.
    @AfterEach
    void cleanup() {
        neo4jClient.query("MATCH (n) WHERE n.id IN $ids DETACH DELETE n")
                .bindAll(Map.of("ids", List.of(adaId, bobId, skillId)))
                .run();
    }

    @Test
    void idor_cannotModifyAnotherUsersSkills() throws Exception {
        mvc.perform(put("/api/v1/people/{id}/skills/{sid}", bobId, skillId)
                .header("Authorization", "Bearer " + adaToken)
                .contentType(APPLICATION_JSON).content("{\"level\":3}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void owner_canSetAndReadOwnSkill() throws Exception {
        mvc.perform(put("/api/v1/people/{id}/skills/{sid}", adaId, skillId)
                .header("Authorization", "Bearer " + adaToken)
                .contentType(APPLICATION_JSON).content("{\"level\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills[0].skillId").value(skillId))
                .andExpect(jsonPath("$.skills[0].level").value(4));

        mvc.perform(get("/api/v1/people/{id}/skills", adaId)
                .header("Authorization", "Bearer " + adaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills[0].skillId").value(skillId));
    }

    @Test
    void invalidLevel_isRejected() throws Exception {
        mvc.perform(put("/api/v1/people/{id}/skills/{sid}", adaId, skillId)
                .header("Authorization", "Bearer " + adaToken)
                .contentType(APPLICATION_JSON).content("{\"level\":9}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wish_rejectedWhenAlreadyMastered() throws Exception {
        mvc.perform(put("/api/v1/people/{id}/skills/{sid}", adaId, skillId)
                .header("Authorization", "Bearer " + adaToken)
                .contentType(APPLICATION_JSON).content("{\"level\":5}"))
                .andExpect(status().isOk());

        mvc.perform(put("/api/v1/people/{id}/wishes/{sid}", adaId, skillId)
                .header("Authorization", "Bearer " + adaToken))
                .andExpect(status().isConflict());
    }

    @Test
    void noToken_isUnauthorized() throws Exception {
        mvc.perform(put("/api/v1/people/{id}/skills/{sid}", adaId, skillId)
                .contentType(APPLICATION_JSON).content("{\"level\":3}"))
                .andExpect(status().isUnauthorized());
    }
}
