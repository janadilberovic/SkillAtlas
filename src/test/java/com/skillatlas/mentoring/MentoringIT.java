package com.skillatlas.mentoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
 * E6.1 and E6.2 against a real Neo4j: the ranking rules of 4.3, the walk of 4.4, both access
 * rules (admin-only matching, owner-or-admin paths) and the mandatory injection case.
 *
 * <p>Fixture names carry a per-run UUID suffix so a shared database cannot make a result
 * ambiguous, and everything created here is deleted again in {@link #cleanup()}.
 */
class MentoringIT extends AbstractNeo4jIT {

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
    MentorshipsRepository mentorshipsRepository;
    @Autowired
    JwtService jwtService;
    @Autowired
    Neo4jClient neo4jClient;

    String neo4jSkill;
    String neo4jSkillId;
    String dockerSkill;
    String dockerSkillId;
    String kafkaSkill;
    String kafkaSkillId;
    String rustSkill;
    String rustSkillId;
    String cobolSkill;
    String cobolSkillId;
    String teamId;
    String projectId;

    String bobId;    // the learner / mentee
    String adaId;    // Neo4j 5, two active mentorships, shares a team with Bob
    String frankId;  // Neo4j 5, no mentorships — outranks Ada on load alone
    String carlId;   // Neo4j 4
    String ginaId;   // Neo4j 3 — exactly on the bar
    String hugoId;   // Neo4j 2 — under the bar
    String danaId;   // Neo4j 5 and the only Rust knower, but soft-deleted
    String eveId;    // Neo4j 3, already mentors Bob on Neo4j

    String adminToken;
    String bobToken;
    String carlToken;

    @BeforeEach
    void seed() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        neo4jSkill = "Neo4j-" + u;
        dockerSkill = "Docker-" + u;
        kafkaSkill = "Kafka-" + u;
        rustSkill = "Rust-" + u;
        cobolSkill = "Cobol-" + u;

        neo4jSkillId = skill(neo4jSkill, SkillCategory.DATABASE);
        dockerSkillId = skill(dockerSkill, SkillCategory.TOOL);
        kafkaSkillId = skill(kafkaSkill, SkillCategory.TOOL);
        rustSkillId = skill(rustSkill, SkillCategory.LANGUAGE);
        cobolSkillId = skill(cobolSkill, SkillCategory.LANGUAGE);

        bobId = createPerson("bob", u, "Byte");
        adaId = createPerson("ada", u, "Lovelace");
        frankId = createPerson("frank", u, "First");
        carlId = createPerson("carl", u, "Cache");
        ginaId = createPerson("gina", u, "Gate");
        hugoId = createPerson("hugo", u, "Hash");
        danaId = createPerson("dana", u, "Delete");
        eveId = createPerson("eve", u, "Edge");

        peopleSkillsService.setSkillLevel(adaId, neo4jSkillId, 5);
        peopleSkillsService.setSkillLevel(frankId, neo4jSkillId, 5);
        peopleSkillsService.setSkillLevel(carlId, neo4jSkillId, 4);
        peopleSkillsService.setSkillLevel(ginaId, neo4jSkillId, 3);
        peopleSkillsService.setSkillLevel(hugoId, neo4jSkillId, 2);
        peopleSkillsService.setSkillLevel(eveId, neo4jSkillId, 3);
        peopleSkillsService.setSkillLevel(danaId, neo4jSkillId, 5);
        peopleSkillsService.setSkillLevel(danaId, rustSkillId, 5);
        // Bob knows Docker a little, Ada knows it well: his path for Docker is the walk to her.
        peopleSkillsService.setSkillLevel(bobId, dockerSkillId, 1);
        peopleSkillsService.setSkillLevel(adaId, dockerSkillId, 4);
        // Cobol he knows at 3 and nobody beats that, so there is nowhere for that walk to go.
        peopleSkillsService.setSkillLevel(bobId, cobolSkillId, 3);

        // Ada carries two mentorships, both on Docker — load is counted across all skills, but the
        // "already mentors them" exclusion is per skill, so she stays a candidate for Neo4j.
        mentorshipsRepository.upsertMentorship(adaId, bobId, dockerSkillId, LocalDate.of(2025, 2, 1));
        mentorshipsRepository.upsertMentorship(adaId, carlId, dockerSkillId, LocalDate.of(2025, 2, 1));
        mentorshipsRepository.upsertMentorship(eveId, bobId, neo4jSkillId, LocalDate.of(2025, 2, 1));

        Team team = teamsService.create(new TeamCreateRequest("Backend-" + u));
        teamId = team.getId();
        teamsService.addMember(teamId, bobId);
        teamsService.addMember(teamId, adaId);
        teamsService.addMember(teamId, danaId);

        // Bob's only project uses Kafka, so it is not a shortcut to Neo4j.
        Project project = projectsService.create(new ProjectCreateRequest(
                "Atlas-" + u, "Knowledge graph", LocalDate.of(2025, 1, 13), null,
                Set.of(kafkaSkillId)));
        projectId = project.getId();
        projectsService.assignMember(projectId, bobId,
                new ProjectMemberRequest("Backend Engineer", LocalDate.of(2025, 1, 13), null));

        peopleService.softDelete(danaId);

        adminToken = jwtService.issue(adaId, Role.ADMIN);
        bobToken = jwtService.issue(bobId, Role.MEMBER);
        carlToken = jwtService.issue(carlId, Role.MEMBER);
    }

    @AfterEach
    void cleanup() {
        neo4jClient.query("MATCH (n) WHERE n.id IN $ids DETACH DELETE n")
                .bindAll(Map.of("ids", List.of(bobId, adaId, frankId, carlId, ginaId, hugoId,
                        danaId, eveId, neo4jSkillId, dockerSkillId, kafkaSkillId, rustSkillId,
                        cobolSkillId, teamId, projectId)))
                .run();
    }

    // --- E6.1 mentor matching ------------------------------------------------

    @Test
    void candidates_rankByLevelThenByLoad() throws Exception {
        mvc.perform(get("/api/v1/people/" + bobId + "/mentor-candidates")
                .param("skill", neo4jSkill)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minLevel").value(3))
                .andExpect(jsonPath("$.skill.name").value(neo4jSkill))
                // Frank and Ada are both 5; Frank mentors nobody, so he goes first.
                .andExpect(jsonPath("$.candidates[0].id").value(frankId))
                .andExpect(jsonPath("$.candidates[0].activeMentorships").value(0))
                .andExpect(jsonPath("$.candidates[1].id").value(adaId))
                .andExpect(jsonPath("$.candidates[1].activeMentorships").value(2))
                .andExpect(jsonPath("$.candidates[2].id").value(carlId))
                .andExpect(jsonPath("$.candidates[3].id").value(ginaId))
                .andExpect(jsonPath("$.candidates.length()").value(4));
    }

    @Test
    void candidates_excludeBelowLevelThreeSelfAndSoftDeleted() throws Exception {
        mvc.perform(get("/api/v1/people/" + bobId + "/mentor-candidates")
                .param("skill", neo4jSkill)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                // Hugo is at 2, Dana is soft-deleted, Bob is the mentee himself.
                .andExpect(jsonPath("$.candidates[?(@.id == '" + hugoId + "')]").doesNotExist())
                .andExpect(jsonPath("$.candidates[?(@.id == '" + danaId + "')]").doesNotExist())
                .andExpect(jsonPath("$.candidates[?(@.id == '" + bobId + "')]").doesNotExist());
    }

    @Test
    void candidates_excludeSomeoneAlreadyMentoringThemOnThatSkill() throws Exception {
        // Eve is at 3 and would otherwise qualify, but she already mentors Bob on Neo4j.
        mvc.perform(get("/api/v1/people/" + bobId + "/mentor-candidates")
                .param("skill", neo4jSkill)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[?(@.id == '" + eveId + "')]").doesNotExist());

        // The exclusion is per skill: Ada mentors Bob on Docker and is still offered for Neo4j.
        mvc.perform(get("/api/v1/people/" + bobId + "/mentor-candidates")
                .param("skill", neo4jSkill)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.candidates[?(@.id == '" + adaId + "')]").exists());
    }

    @Test
    void candidates_areAdminOnly() throws Exception {
        mvc.perform(get("/api/v1/people/" + bobId + "/mentor-candidates")
                .param("skill", neo4jSkill)
                .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/people/" + bobId + "/mentor-candidates")
                .param("skill", neo4jSkill))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void candidates_missingPersonOrSkill_isNotFound() throws Exception {
        mvc.perform(get("/api/v1/people/nope/mentor-candidates")
                .param("skill", neo4jSkill)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/people/" + bobId + "/mentor-candidates")
                .param("skill", "cobol-" + UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        // A soft-deleted person has no mentor candidates either — they are gone from every read.
        mvc.perform(get("/api/v1/people/" + danaId + "/mentor-candidates")
                .param("skill", neo4jSkill)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void cypherInjection_throughSkillParam_deletesNothing() throws Exception {
        long nodesBefore = countNodes();

        mvc.perform(get("/api/v1/people/" + bobId + "/mentor-candidates")
                .param("skill", "React'}) DETACH DELETE (n) //")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        assertThat(countNodes()).isEqualTo(nodesBefore);
        // And the endpoint still answers normally afterwards.
        mvc.perform(get("/api/v1/people/" + bobId + "/mentor-candidates")
                .param("skill", neo4jSkill)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates.length()").value(4));
    }

    // --- E6.1 confirmation ---------------------------------------------------

    @Test
    void confirm_createsTheRelationshipAndIsIdempotent() throws Exception {
        mvc.perform(post("/api/v1/mentorships")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(frankId, bobId, neo4jSkillId))
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mentorId").value(frankId))
                .andExpect(jsonPath("$.skill.name").value(neo4jSkill));

        // MERGE, not CREATE: a second confirmation is the same relationship.
        mvc.perform(post("/api/v1/mentorships")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(frankId, bobId, neo4jSkillId))
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated());

        assertThat(countMentorships(frankId, bobId)).isEqualTo(1);
    }

    @Test
    void confirm_stampsSinceItself() throws Exception {
        // Mass assignment: `since` is not part of the request record, so this value is ignored.
        String tampered = """
                {"mentorId":"%s","menteeId":"%s","skillId":"%s","since":"1999-01-01"}
                """.formatted(frankId, bobId, neo4jSkillId);

        mvc.perform(post("/api/v1/mentorships")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tampered)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.since").value(LocalDate.now().toString()));
    }

    @Test
    void confirm_rejectsSelfMentoringAndMentorsUnderTheBar() throws Exception {
        mvc.perform(post("/api/v1/mentorships")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(bobId, bobId, neo4jSkillId))
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        // Hugo knows Neo4j at 2 — the rule holds at the write, not only in the candidate list.
        mvc.perform(post("/api/v1/mentorships")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(hugoId, bobId, neo4jSkillId))
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        assertThat(countMentorships(hugoId, bobId)).isZero();
    }

    @Test
    void confirm_rejectsSoftDeletedAndUnknownParties() throws Exception {
        mvc.perform(post("/api/v1/mentorships")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(danaId, bobId, neo4jSkillId))
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/mentorships")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(frankId, bobId, "no-such-skill"))
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirm_isAdminOnly() throws Exception {
        mvc.perform(post("/api/v1/mentorships")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(frankId, bobId, neo4jSkillId))
                .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());

        assertThat(countMentorships(frankId, bobId)).isZero();
    }

    @Test
    void remove_tearsTheRelationshipDownAndIs404WhenItIsNotThere() throws Exception {
        mvc.perform(delete("/api/v1/mentorships")
                .param("mentorId", adaId).param("menteeId", bobId).param("skillId", dockerSkillId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(countMentorships(adaId, bobId)).isZero();

        mvc.perform(delete("/api/v1/mentorships")
                .param("mentorId", adaId).param("menteeId", bobId).param("skillId", dockerSkillId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void remove_isAdminOnly() throws Exception {
        mvc.perform(delete("/api/v1/mentorships")
                .param("mentorId", adaId).param("menteeId", bobId).param("skillId", dockerSkillId)
                .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());

        assertThat(countMentorships(adaId, bobId)).isEqualTo(1);
    }

    // --- E6.2 learning path --------------------------------------------------

    @Test
    void learningPath_walksThroughTheTeamAndNamesTheNearestMentor() throws Exception {
        // Bob -MEMBER_OF-> team <-MEMBER_OF- Ada -KNOWS-> Neo4j
        mvc.perform(get("/api/v1/people/" + bobId + "/learning-path")
                .param("skill", neo4jSkill)
                .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.steps").value(3))
                .andExpect(jsonPath("$.nodes.length()").value(4))
                .andExpect(jsonPath("$.nearestMentor.id").value(adaId))
                .andExpect(jsonPath("$.nearestMentor.level").value(5))
                .andExpect(jsonPath("$.ownLevel").doesNotExist());
    }

    @Test
    void learningPath_walksThroughAProject() throws Exception {
        mvc.perform(get("/api/v1/people/" + bobId + "/learning-path")
                .param("skill", kafkaSkill)
                .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.steps").value(2))
                .andExpect(jsonPath("$.nodes[1].kind").value("PROJECT"))
                // Nobody else stands on this walk, so there is no mentor to name.
                .andExpect(jsonPath("$.nearestMentor").doesNotExist());
    }

    @Test
    void learningPath_forASkillYouAlreadyKnow_leadsToSomeoneWhoKnowsItBetter() throws Exception {
        // Bob is at 1 on Docker. The walk to the skill would be his own KNOWS edge and say nothing,
        // so it is redrawn towards Ada (4), who shares his team.
        mvc.perform(get("/api/v1/people/" + bobId + "/learning-path")
                .param("skill", dockerSkill)
                .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.ownLevel").value(1))
                .andExpect(jsonPath("$.steps").value(2))
                .andExpect(jsonPath("$.nodes[0].id").value(bobId))
                .andExpect(jsonPath("$.nodes[2].id").value(adaId))
                .andExpect(jsonPath("$.nearestMentor.id").value(adaId))
                .andExpect(jsonPath("$.nearestMentor.level").value(4))
                .andExpect(jsonPath("$.nearestMentor.onPath").value(true));
    }

    @Test
    void learningPath_forASkillNobodyBeatsYouAt_staysTheOneHopWalk() throws Exception {
        // Bob is at 3 on Cobol; a mentor would have to be at 4, and nobody is.
        mvc.perform(get("/api/v1/people/" + bobId + "/learning-path")
                .param("skill", cobolSkill)
                .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownLevel").value(3))
                .andExpect(jsonPath("$.steps").value(1))
                .andExpect(jsonPath("$.nearestMentor").doesNotExist());
    }

    @Test
    void learningPath_neverRoutesThroughASoftDeletedPerson() throws Exception {
        // Dana is Bob's team-mate and the only person who knows Rust — but she is deleted, so the
        // only walk that exists is not a walk at all.
        mvc.perform(get("/api/v1/people/" + bobId + "/learning-path")
                .param("skill", rustSkill)
                .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                // No route is an answer, not an error: both ends exist, nothing joins them.
                .andExpect(jsonPath("$.found").value(false))
                .andExpect(jsonPath("$.nodes").isEmpty())
                .andExpect(jsonPath("$.skill.name").value(rustSkill));
    }

    @Test
    void learningPath_isOwnerOrAdminOnly() throws Exception {
        // IDOR: Carl is a member asking for Bob's path.
        mvc.perform(get("/api/v1/people/" + bobId + "/learning-path")
                .param("skill", neo4jSkill)
                .header("Authorization", "Bearer " + carlToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/people/" + bobId + "/learning-path")
                .param("skill", neo4jSkill)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/people/" + bobId + "/learning-path")
                .param("skill", neo4jSkill))
                .andExpect(status().isUnauthorized());
    }

    private String body(String mentorId, String menteeId, String skillId) {
        return """
                {"mentorId":"%s","menteeId":"%s","skillId":"%s"}
                """.formatted(mentorId, menteeId, skillId);
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

    private long countMentorships(String mentorId, String menteeId) {
        return neo4jClient.query("""
                MATCH (:Person {id: $mentorId})-[r:MENTORS]->(:Person {id: $menteeId})
                RETURN count(r) AS total
                """)
                .bindAll(Map.of("mentorId", mentorId, "menteeId", menteeId))
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("total").asLong())
                .one()
                .orElse(0L);
    }

    private long countNodes() {
        return neo4jClient.query("MATCH (n) RETURN count(n) AS total")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("total").asLong())
                .one()
                .orElse(0L);
    }
}
