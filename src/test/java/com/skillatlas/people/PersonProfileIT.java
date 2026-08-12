package com.skillatlas.people;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Integration tests for E4.2: the aggregate behind {@code GET /api/v1/people/{id}} against a real
 * Neo4j — every branch, both directions of MENTORS, the soft-delete filter on the far side of a
 * relationship, the capped neighbourhood, and the mandatory Cypher-injection case.
 *
 * <p>Fixture names carry a per-run UUID suffix so a shared database can't make results ambiguous,
 * and everything created here is deleted again in {@link #cleanup()}.
 */
class PersonProfileIT extends AbstractNeo4jIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    PeopleService peopleService;
    @Autowired
    PeopleSkillsService peopleSkillsService;
    @Autowired
    MentorshipsRepository mentorshipsRepository;
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

    String neo4jSkill;
    String dockerSkill;
    String neo4jSkillId;
    String dockerSkillId;
    String teamName;
    String teamId;
    String projectName;
    String projectId;
    String adaId;      // the profile under test
    String bobId;      // Ada's mentee
    String carlId;     // mentors Ada
    String danaId;     // mentors Ada, then soft-deleted
    String loneId;     // no relationships at all
    String deletedId;  // soft-deleted; their own profile must 404
    String token;

    @BeforeEach
    void seed() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        neo4jSkill = "Neo4j-" + u;
        dockerSkill = "Docker-" + u;
        teamName = "Backend-" + u;
        projectName = "Atlas-" + u;

        Skill neo4j = skillsService.create(new SkillCreateRequest(neo4jSkill, SkillCategory.DATABASE, "#4581C3"));
        Skill docker = skillsService.create(new SkillCreateRequest(dockerSkill, SkillCategory.TOOL, "#2496ED"));
        neo4jSkillId = neo4j.getId();
        dockerSkillId = docker.getId();

        adaId = createPerson("ada", u, "Lovelace");
        bobId = createPerson("bob", u, "Byte");
        carlId = createPerson("carl", u, "Cache");
        danaId = createPerson("dana", u, "Delete");
        loneId = createPerson("lone", u, "Wolf");
        deletedId = createPerson("gone", u, "Gone");

        peopleSkillsService.setSkillLevel(adaId, neo4jSkillId, 5);
        peopleSkillsService.setSkillLevel(adaId, dockerSkillId, 3);
        peopleSkillsService.addWish(adaId, dockerSkillId);

        Team team = teamsService.create(new TeamCreateRequest(teamName));
        teamId = team.getId();
        teamsService.addMember(teamId, adaId);

        // The project USES Docker, which is the second hop the neighbourhood is allowed to draw.
        Project project = projectsService.create(new ProjectCreateRequest(
                projectName, "Knowledge graph", LocalDate.of(2025, 1, 13), null,
                Set.of(dockerSkillId)));
        projectId = project.getId();
        projectsService.assignMember(projectId, adaId,
                new ProjectMemberRequest("Backend Engineer", LocalDate.of(2025, 1, 13), null));

        // Mentoring in both directions, plus one mentor who is then soft-deleted.
        mentorshipsRepository.upsertMentorship(adaId, bobId, dockerSkillId, LocalDate.of(2025, 2, 1));
        mentorshipsRepository.upsertMentorship(carlId, adaId, neo4jSkillId, LocalDate.of(2024, 11, 1));
        mentorshipsRepository.upsertMentorship(danaId, adaId, neo4jSkillId, LocalDate.of(2024, 10, 1));
        peopleService.softDelete(danaId);
        peopleService.softDelete(deletedId);

        token = jwtService.issue(bobId, Role.MEMBER);
    }

    @AfterEach
    void cleanup() {
        neo4jClient.query("MATCH (n) WHERE n.id IN $ids DETACH DELETE n")
                .bindAll(Map.of("ids", List.of(adaId, bobId, carlId, danaId, loneId, deletedId,
                        neo4jSkillId, dockerSkillId, teamId, projectId)))
                .run();
    }

    private String createPerson(String first, String suffix, String last) {
        Person person = peopleService.create(new PersonCreateRequest(
                first + "-" + suffix + "@test.com", "Password123!", first, last, "Engineer", null,
                Role.MEMBER));
        return person.getId();
    }

    private long countPeople() {
        return neo4jClient.query("MATCH (p:Person) RETURN count(p) AS total")
                .fetchAs(Long.class)
                .mappedBy((ts, record) -> record.get("total").asLong())
                .one()
                .orElseThrow();
    }

    @Test
    void profile_aggregatesSkillsWishesProjectsAndTeams() throws Exception {
        mvc.perform(get("/api/v1/people/" + adaId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                // Still a superset of PersonResponse — the shallow fields did not move.
                .andExpect(jsonPath("$.id").value(adaId))
                .andExpect(jsonPath("$.firstName").value("ada"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.active").value(true))
                // ... and never leaks the hash or the soft-delete bookkeeping.
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.isDeleted").doesNotExist())
                .andExpect(jsonPath("$.deletedAt").doesNotExist())
                .andExpect(jsonPath("$.teams[0]").value(teamName))
                // Strongest skill first; level and since come off the KNOWS relationship.
                .andExpect(jsonPath("$.skills.length()").value(2))
                .andExpect(jsonPath("$.skills[0].name").value(neo4jSkill))
                .andExpect(jsonPath("$.skills[0].level").value(5))
                .andExpect(jsonPath("$.skills[0].category").value("DATABASE"))
                .andExpect(jsonPath("$.skills[0].since").exists())
                .andExpect(jsonPath("$.skills[1].level").value(3))
                .andExpect(jsonPath("$.wishes.length()").value(1))
                .andExpect(jsonPath("$.wishes[0].name").value(dockerSkill))
                .andExpect(jsonPath("$.projects.length()").value(1))
                .andExpect(jsonPath("$.projects[0].name").value(projectName))
                .andExpect(jsonPath("$.projects[0].role").value("Backend Engineer"))
                .andExpect(jsonPath("$.projects[0].active").value(true))
                .andExpect(jsonPath("$.projects[0].to").doesNotExist())
                .andExpect(jsonPath("$.projects[0].uses[0]").value(dockerSkill));
    }

    @Test
    void profile_showsMentoringInBothDirections() throws Exception {
        mvc.perform(get("/api/v1/people/" + adaId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentoring.mentees.length()").value(1))
                .andExpect(jsonPath("$.mentoring.mentees[0].personId").value(bobId))
                .andExpect(jsonPath("$.mentoring.mentees[0].skill").value(dockerSkill))
                .andExpect(jsonPath("$.mentoring.mentees[0].since").value("2025-02-01"))
                // Carl mentors Ada; Dana did too but is soft-deleted.
                .andExpect(jsonPath("$.mentoring.mentors.length()").value(1))
                .andExpect(jsonPath("$.mentoring.mentors[0].personId").value(carlId))
                .andExpect(jsonPath("$.mentoring.mentors[?(@.personId == '" + danaId + "')]")
                        .doesNotExist());
    }

    @Test
    void softDeletedCounterpart_isHiddenFromTheNeighbourhoodToo() throws Exception {
        mvc.perform(get("/api/v1/people/" + adaId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.neighbourhood.nodes[?(@.id == '" + danaId + "')]")
                        .doesNotExist())
                .andExpect(jsonPath("$.neighbourhood.edges[?(@.source == '" + danaId + "')]")
                        .doesNotExist());
    }

    @Test
    void neighbourhood_hasTheRootPersonItsHopsAndTheProjectSecondHop() throws Exception {
        mvc.perform(get("/api/v1/people/" + adaId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                // The person themselves is always node zero, whatever else is around them.
                .andExpect(jsonPath("$.neighbourhood.nodes[0].id").value(adaId))
                .andExpect(jsonPath("$.neighbourhood.nodes[0].kind").value("PERSON"))
                .andExpect(jsonPath("$.neighbourhood.nodes[?(@.id == '" + projectId + "')].kind")
                        .value("PROJECT"))
                .andExpect(jsonPath("$.neighbourhood.nodes[?(@.id == '" + teamId + "')].kind")
                        .value("TEAM"))
                .andExpect(jsonPath("$.neighbourhood.edges[?(@.type == 'KNOWS')]").exists())
                .andExpect(jsonPath("$.neighbourhood.edges[?(@.type == 'MEMBER_OF')]").exists())
                .andExpect(jsonPath("$.neighbourhood.edges[?(@.type == 'WORKED_ON')]").exists())
                // Second hop: the project USES Docker, so the skill hangs off the project.
                .andExpect(jsonPath("$.neighbourhood.edges[?(@.source == '" + projectId
                        + "' && @.target == '" + dockerSkillId + "' && @.type == 'USES')]").exists())
                .andExpect(jsonPath("$.neighbourhood.truncated").value(false));
    }

    @Test
    void personWithNoRelationships_getsEmptyListsNotNulls() throws Exception {
        mvc.perform(get("/api/v1/people/" + loneId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams").isEmpty())
                .andExpect(jsonPath("$.skills").isEmpty())
                .andExpect(jsonPath("$.wishes").isEmpty())
                .andExpect(jsonPath("$.projects").isEmpty())
                .andExpect(jsonPath("$.mentoring.mentees").isEmpty())
                .andExpect(jsonPath("$.mentoring.mentors").isEmpty())
                .andExpect(jsonPath("$.neighbourhood.edges").isEmpty())
                .andExpect(jsonPath("$.neighbourhood.nodes.length()").value(1))
                .andExpect(jsonPath("$.neighbourhood.nodes[0].id").value(loneId));
    }

    @Test
    void softDeletedPerson_hasNoProfile() throws Exception {
        mvc.perform(get("/api/v1/people/" + deletedId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownPerson_is404() throws Exception {
        mvc.perform(get("/api/v1/people/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void withoutAToken_is401() throws Exception {
        mvc.perform(get("/api/v1/people/" + adaId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cypherInjectionThroughTheId_deletesNothing() throws Exception {
        long before = countPeople();

        // The trailing `//` of the canonical payload never reaches the application: an encoded
        // slash in a path segment is rejected by the servlet container first (400).
        mvc.perform(get("/api/v1/people/{id}", "React'}) DETACH DELETE (n) //")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        // Without the slashes the payload does reach the repository, which is the case that
        // matters: bound as $id, it is simply an id no person has.
        mvc.perform(get("/api/v1/people/{id}", "React'}) DETACH DELETE (n)")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        // The 404 alone would also be true of a wiped database — the count is the real assertion.
        assertThat(countPeople()).isEqualTo(before);
        mvc.perform(get("/api/v1/people/" + adaId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills.length()").value(2));
    }
}
