package com.skillatlas.projects;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.skillatlas.people.PeopleService;
import com.skillatlas.people.domain.Person;
import com.skillatlas.people.dto.PersonCreateRequest;
import com.skillatlas.people.enums.Role;
import com.skillatlas.security.JwtService;
import com.skillatlas.skills.SkillsService;
import com.skillatlas.skills.domain.Skill;
import com.skillatlas.skills.dto.SkillCreateRequest;
import com.skillatlas.skills.enums.SkillCategory;
import com.skillatlas.support.AbstractNeo4jIT;

// Creating a project from the admin modal: the USES edges it carries and the status it lands in.
class ProjectCreateIT extends AbstractNeo4jIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    SkillsService skillsService;
    @Autowired
    PeopleService peopleService;
    @Autowired
    JwtService jwtService;
    @Autowired
    Neo4jClient neo4jClient;

    String suffix;
    String skillId;
    String adminId;
    String memberId;
    String adminToken;
    String memberToken;
    String projectName;

    @BeforeEach
    void seed() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        Skill skill = skillsService.create(new SkillCreateRequest("Cypher-" + suffix, SkillCategory.DATABASE, null));
        Person admin = peopleService.create(new PersonCreateRequest(
                "admin-" + suffix + "@test.com", "Password123!", "Site", "Admin", "Admin", null, Role.ADMIN));
        Person member = peopleService.create(new PersonCreateRequest(
                "member-" + suffix + "@test.com", "Password123!", "Mia", "Member", "Engineer", null, Role.MEMBER));
        skillId = skill.getId();
        adminId = admin.getId();
        memberId = member.getId();
        adminToken = jwtService.issue(adminId, Role.ADMIN);
        memberToken = jwtService.issue(memberId, Role.MEMBER);
        projectName = "Meridian-" + suffix;
    }

    @AfterEach
    void cleanup() {
        neo4jClient.query("MATCH (n) WHERE n.id IN $ids OR n.name = $project DETACH DELETE n")
                .bindAll(Map.of("ids", List.of(skillId, adminId, memberId), "project", projectName))
                .run();
    }

    private String body(String activeField) {
        return """
                {"name":"%s","description":"Internal staffing recommendations",
                 "startDate":"2026-09-01","skillIds":["%s"]%s}
                """.formatted(projectName, skillId, activeField);
    }

    @Test
    void create_asAdmin_returns201WithItsUsesEdges() throws Exception {
        mvc.perform(post("/api/v1/projects")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON).content(body("")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(projectName))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.skills[0].id").value(skillId));
    }

    // The modal lets an admin file a project that is already over, so `active` has to survive create.
    @Test
    void create_withActiveFalse_landsArchived() throws Exception {
        mvc.perform(post("/api/v1/projects")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON).content(body(",\"endDate\":\"2026-01-31\",\"active\":false")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void create_withUnknownSkill_returns404() throws Exception {
        mvc.perform(post("/api/v1/projects")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON)
                .content("""
                        {"name":"%s","startDate":"2026-09-01","skillIds":["no-such-skill"]}
                        """.formatted(projectName)))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_asMember_isForbidden() throws Exception {
        mvc.perform(post("/api/v1/projects")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(APPLICATION_JSON).content(body("")))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_withoutName_isRejected() throws Exception {
        mvc.perform(post("/api/v1/projects")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON)
                .content("""
                        {"name":"  ","startDate":"2026-09-01","skillIds":["%s"]}
                        """.formatted(skillId)))
                .andExpect(status().isBadRequest());
    }
}
