package com.skillatlas.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillatlas.people.PeopleService;
import com.skillatlas.people.PeopleSkillsService;
import com.skillatlas.people.domain.Person;
import com.skillatlas.people.dto.PersonCreateRequest;
import com.skillatlas.people.enums.Role;
import com.skillatlas.projects.ProjectsService;
import com.skillatlas.projects.domain.Project;
import com.skillatlas.projects.dto.ProjectCreateRequest;
import com.skillatlas.projects.dto.ProjectMemberRequest;
import com.skillatlas.security.JwtService;
import com.skillatlas.skills.SkillsService;
import com.skillatlas.skills.domain.Skill;
import com.skillatlas.skills.dto.SkillCreateRequest;
import com.skillatlas.skills.enums.SkillCategory;
import com.skillatlas.support.AbstractNeo4jIT;
import com.skillatlas.teams.TeamsService;
import com.skillatlas.teams.domain.Team;
import com.skillatlas.teams.dto.TeamCreateRequest;

/**
 * E6.3 against a real Neo4j.
 *
 * <p>The gap and bus-factor widgets are asserted on the fixture's own UUID-suffixed skills, which
 * nobody outside this test knows. The two company-wide counters — metrics and the mapping queue —
 * are asserted as <em>deltas</em> instead: their absolute value depends on whatever else lives in
 * the database this suite is pointed at.
 */
class DashboardIT extends AbstractNeo4jIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    PeopleService peopleService;
    @Autowired
    PeopleSkillsService peopleSkillsService;
    @Autowired
    SkillsService skillsService;
    @Autowired
    TeamsService teamsService;
    @Autowired
    ProjectsService projectsService;
    @Autowired
    JwtService jwtService;
    @Autowired
    Neo4jClient neo4jClient;
    @Autowired
    ObjectMapper objectMapper;

    String neo4jSkill;
    String neo4jSkillId;
    String kafkaSkill;
    String kafkaSkillId;
    String dockerSkill;
    String dockerSkillId;
    String teamName;
    String teamId;
    String projectName;
    String projectId;

    String adaId;   // knows Neo4j (alone) and Docker
    String carlId;  // knows Docker
    String bobId;   // knows nothing — the mapping queue
    String danaId;  // knows Neo4j too, but soft-deleted

    String adminToken;
    String memberToken;

    @BeforeEach
    void seed() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        neo4jSkill = "Neo4j-" + u;
        kafkaSkill = "Kafka-" + u;
        dockerSkill = "Docker-" + u;
        teamName = "Backend-" + u;
        projectName = "Atlas-" + u;

        neo4jSkillId = skill(neo4jSkill, SkillCategory.DATABASE);
        kafkaSkillId = skill(kafkaSkill, SkillCategory.TOOL);
        dockerSkillId = skill(dockerSkill, SkillCategory.TOOL);

        adaId = createPerson("ada", u, "Lovelace");
        carlId = createPerson("carl", u, "Cache");
        bobId = createPerson("bob", u, "Byte");
        danaId = createPerson("dana", u, "Delete");

        peopleSkillsService.setSkillLevel(adaId, neo4jSkillId, 5);
        peopleSkillsService.setSkillLevel(adaId, dockerSkillId, 3);
        peopleSkillsService.setSkillLevel(carlId, dockerSkillId, 4);
        peopleSkillsService.setSkillLevel(danaId, neo4jSkillId, 5);

        Team team = teamsService.create(new TeamCreateRequest(teamName));
        teamId = team.getId();
        teamsService.addMember(teamId, adaId);
        teamsService.addMember(teamId, carlId);
        teamsService.addMember(teamId, bobId);

        // The team's project needs all three skills; the team knows one of them properly.
        Project project = projectsService.create(new ProjectCreateRequest(
                projectName, "Knowledge graph", LocalDate.of(2025, 1, 13), null,
                Set.of(neo4jSkillId, kafkaSkillId, dockerSkillId)));
        projectId = project.getId();
        projectsService.assignMember(projectId, adaId,
                new ProjectMemberRequest("Backend Engineer", LocalDate.of(2025, 1, 13), null));
        projectsService.assignMember(projectId, carlId,
                new ProjectMemberRequest("Backend Engineer", LocalDate.of(2025, 1, 13), null));

        peopleService.softDelete(danaId);

        adminToken = jwtService.issue(adaId, Role.ADMIN);
        memberToken = jwtService.issue(carlId, Role.MEMBER);
    }

    @AfterEach
    void cleanup() {
        neo4jClient.query("MATCH (n) WHERE n.id IN $ids DETACH DELETE n")
                .bindAll(Map.of("ids", List.of(adaId, carlId, bobId, danaId, neo4jSkillId,
                        kafkaSkillId, dockerSkillId, teamId, projectId)))
                .run();
    }

    @Test
    void dashboard_isAdminOnly() throws Exception {
        mvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void skillGap_listsWhatTheTeamsProjectsUseButTheTeamBarelyKnows() throws Exception {
        String row = "$.content[?(@.team == '" + teamName + "' && @.skill == '";

        // Asked for a wide page rather than read off the overview: this suite may run against a
        // database that already holds other teams' gaps, and the fixture's rows need not land on
        // page one.
        mvc.perform(get("/api/v1/dashboard/skill-gap")
                .param("size", "100")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                // Neo4j: only Ada on this team knows it.
                .andExpect(jsonPath(row + neo4jSkill + "')].knownBy").value(contains(1)))
                .andExpect(jsonPath(row + neo4jSkill + "')].projects[0]").value(contains(projectName)))
                // Kafka: the project needs it and nobody on the team knows it at all.
                .andExpect(jsonPath(row + kafkaSkill + "')].knownBy").value(contains(0)))
                // Docker: two people know it, so it is not a gap.
                .andExpect(jsonPath(row + dockerSkill + "')]").doesNotExist());
    }

    @Test
    void skillGap_pagesAndAgreesWithItsOwnCount() throws Exception {
        JsonNode all = page(0, 100);
        long total = all.path("totalElements").asLong();
        // The fixture alone contributes the team's Neo4j and Kafka rows.
        assertThat(total).isGreaterThanOrEqualTo(2);

        JsonNode first = page(0, 1);
        JsonNode second = page(1, 1);
        assertThat(first.path("content")).hasSize(1);
        assertThat(first.path("totalElements").asLong()).isEqualTo(total);
        assertThat(first.path("totalPages").asLong()).isEqualTo(total);
        // Paging walks the list rather than repeating its head.
        assertThat(second.path("content").get(0)).isNotEqualTo(first.path("content").get(0));
    }

    @Test
    void skillGap_pageSizeIsCapped() throws Exception {
        assertThat(page(0, 5000).path("size").asInt()).isEqualTo(100);
    }

    @Test
    void overview_embedsTheFirstPageOfTheGapAndSaysHowManyThereAre() throws Exception {
        JsonNode gap = overview().path("skillGap");

        assertThat(gap.path("page").asInt()).isZero();
        assertThat(gap.path("content").size()).isLessThanOrEqualTo(gap.path("size").asInt());
        assertThat(gap.path("totalElements").asLong())
                .isGreaterThanOrEqualTo(gap.path("content").size());
    }

    @Test
    void skillGap_isAdminOnly() throws Exception {
        mvc.perform(get("/api/v1/dashboard/skill-gap")
                .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/dashboard/skill-gap"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void busFactor_namesTheSinglePersonASkillDependsOn() throws Exception {
        // Dana knows Neo4j as well, but she is soft-deleted: the skill still hangs on Ada alone.
        mvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busFactor[?(@.skill == '" + neo4jSkill + "')].personId")
                        .value(contains(adaId)))
                .andExpect(jsonPath("$.busFactor[?(@.skill == '" + neo4jSkill + "')].personName")
                        .value(contains("ada Lovelace")))
                // Docker is known by two people; Kafka by nobody. Neither is a bus factor.
                .andExpect(jsonPath("$.busFactor[?(@.skill == '" + dockerSkill + "')]").doesNotExist())
                .andExpect(jsonPath("$.busFactor[?(@.skill == '" + kafkaSkill + "')]").doesNotExist());
    }

    @Test
    void mappingQueue_countsExactlyThePeopleWithNoSkills() throws Exception {
        long before = overview().path("mappingQueue").path("total").asLong();

        // Bob is in the queue because he knows nothing; giving him one skill takes him out of it.
        peopleSkillsService.setSkillLevel(bobId, dockerSkillId, 2);

        assertThat(overview().path("mappingQueue").path("total").asLong()).isEqualTo(before - 1);
    }

    @Test
    void metrics_countOnlyPeopleWhoAreStillHere() throws Exception {
        long before = overview().path("metrics").path("people").asLong();

        peopleService.softDelete(carlId);

        assertThat(overview().path("metrics").path("people").asLong()).isEqualTo(before - 1);
    }

    private JsonNode page(int page, int size) throws Exception {
        String json = mvc.perform(get("/api/v1/dashboard/skill-gap")
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size))
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private JsonNode overview() throws Exception {
        String json = mvc.perform(get("/api/v1/dashboard")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private String skill(String name, SkillCategory category) {
        Skill skill = skillsService.create(new SkillCreateRequest(name, category, "#4581C3"));
        return skill.getId();
    }

    private String createPerson(String first, String suffix, String last) {
        Person person = peopleService.create(new PersonCreateRequest(
                first + "-" + suffix + "@test.com", "Password123!", first, last, "Engineer", null,
                Role.MEMBER));
        return person.getId();
    }
}
