package com.skillatlas.finder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.skillatlas.people.PeopleSkillsService;
import com.skillatlas.people.domain.Person;
import com.skillatlas.people.dto.PersonCreateRequest;
import com.skillatlas.people.enums.Role;
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
 * Integration tests for E4.1: AND semantics, ranking, soft-delete filtering, the team filter and
 * the mandatory Cypher-injection case — against a real Neo4j.
 *
 * <p>Fixture names carry a per-run UUID suffix so a shared database can't make results ambiguous,
 * and everything created here is deleted again in {@link #cleanup()}.
 */
class FinderIT extends AbstractNeo4jIT {

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
    JwtService jwtService;
    @Autowired
    Neo4jClient neo4jClient;

    String neo4jSkill;
    String dockerSkill;
    String neo4jSkillId;
    String dockerSkillId;
    String teamName;
    String teamId;
    String adaId;
    String bobId;
    String carlId;
    String danaId;
    String token;

    @BeforeEach
    void seed() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        neo4jSkill = "Neo4j-" + u;
        dockerSkill = "Docker-" + u;
        teamName = "Backend-" + u;

        Skill neo4j = skillsService.create(new SkillCreateRequest(neo4jSkill, SkillCategory.DATABASE, "#4581C3"));
        Skill docker = skillsService.create(new SkillCreateRequest(dockerSkill, SkillCategory.TOOL, "#2496ED"));
        neo4jSkillId = neo4j.getId();
        dockerSkillId = docker.getId();

        adaId = createPerson("ada", u, "Lovelace");
        bobId = createPerson("bob", u, "Byte");
        carlId = createPerson("carl", u, "Cache");
        danaId = createPerson("dana", u, "Delete");

        // Ada knows both, strongest (5 + 3 = 8). Bob knows both, weaker (2 + 1 = 3).
        peopleSkillsService.setSkillLevel(adaId, neo4jSkillId, 5);
        peopleSkillsService.setSkillLevel(adaId, dockerSkillId, 3);
        peopleSkillsService.setSkillLevel(bobId, neo4jSkillId, 2);
        peopleSkillsService.setSkillLevel(bobId, dockerSkillId, 1);
        // Carl knows only one of the two — must fall out of an AND query.
        peopleSkillsService.setSkillLevel(carlId, neo4jSkillId, 5);
        // Dana knows both but is soft-deleted — must never surface.
        peopleSkillsService.setSkillLevel(danaId, neo4jSkillId, 5);
        peopleSkillsService.setSkillLevel(danaId, dockerSkillId, 5);
        peopleService.softDelete(danaId);

        Team team = teamsService.create(new TeamCreateRequest(teamName));
        teamId = team.getId();
        // No MEMBER_OF write endpoint exists yet, so the fixture wires the edge directly.
        // Separate MATCH clauses: one clause with two disconnected patterns triggers Neo4j's
        // CartesianProduct notification, which spams the test log.
        neo4jClient.query("""
                MATCH (p:Person {id: $pid})
                MATCH (t:Team {id: $tid})
                MERGE (p)-[:MEMBER_OF]->(t)
                """)
                .bindAll(Map.of("pid", adaId, "tid", teamId))
                .run();

        token = jwtService.issue(adaId, Role.MEMBER);
    }

    @AfterEach
    void cleanup() {
        neo4jClient.query("MATCH (n) WHERE n.id IN $ids DETACH DELETE n")
                .bindAll(Map.of("ids",
                        List.of(adaId, bobId, carlId, danaId, neo4jSkillId, dockerSkillId, teamId)))
                .run();
    }

    private String createPerson(String first, String suffix, String last) {
        Person person = peopleService.create(new PersonCreateRequest(
                first + "-" + suffix + "@test.com", "Password123!", first, last, "Engineer", null, Role.MEMBER));
        return person.getId();
    }

    @Test
    void andAcrossSkills_ranksBySummedLevel() throws Exception {
        mvc.perform(get("/api/v1/experts")
                .param("skills", neo4jSkill + "," + dockerSkill)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(adaId))
                .andExpect(jsonPath("$.content[0].score").value(8))
                .andExpect(jsonPath("$.content[0].matchedSkills.length()").value(2))
                .andExpect(jsonPath("$.content[1].id").value(bobId))
                .andExpect(jsonPath("$.content[1].score").value(3));
    }

    @Test
    void partialMatch_isExcluded() throws Exception {
        // Carl knows Neo4j but not Docker: he is an expert for one skill, not for the pair.
        mvc.perform(get("/api/v1/experts")
                .param("skills", neo4jSkill)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + carlId + "')]").exists());

        mvc.perform(get("/api/v1/experts")
                .param("skills", neo4jSkill + "," + dockerSkill)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + carlId + "')]").doesNotExist());
    }

    @Test
    void levelThreshold_dropsPeopleBelowTheBar() throws Exception {
        // Bob knows both, but only at 2 and 1 — "≥ 3" is what separates him from Ada.
        mvc.perform(get("/api/v1/experts")
                .param("skills", neo4jSkill + ">=3," + dockerSkill + ">=3")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(adaId));
    }

    @Test
    void levelThreshold_isPerSkillNotPerQuery() throws Exception {
        // Ada is 5 on Neo4j and 3 on Docker: she clears "Neo4j ≥ 5 + Docker ≥ 3" ...
        mvc.perform(get("/api/v1/experts")
                .param("skills", neo4jSkill + ">=5," + dockerSkill + ">=3")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(adaId));

        // ... and fails the same query with the Docker bar raised, even though Neo4j still passes.
        mvc.perform(get("/api/v1/experts")
                .param("skills", neo4jSkill + ">=5," + dockerSkill + ">=4")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void strictGreaterThan_excludesTheLevelWritten() throws Exception {
        // Ada is exactly 3 on Docker, so "> 3" must drop her while "≥ 3" keeps her.
        mvc.perform(get("/api/v1/experts")
                .param("skills", dockerSkill + ">3")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        mvc.perform(get("/api/v1/experts")
                .param("skills", dockerSkill + ">=3")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(adaId));
    }

    @Test
    void levelOutsideOneToFive_isBadRequest() throws Exception {
        mvc.perform(get("/api/v1/experts")
                .param("skills", neo4jSkill + ">=6")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void teamMembership_isReturnedWithEachExpert() throws Exception {
        mvc.perform(get("/api/v1/experts")
                .param("skills", neo4jSkill + "," + dockerSkill)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(adaId))
                .andExpect(jsonPath("$.content[0].teams[0]").value(teamName))
                // Bob is in no team; the field is an empty list, never null.
                .andExpect(jsonPath("$.content[1].teams").isEmpty());
    }

    @Test
    void coverage_countsKnowersAndNamesOnlyTheExperts() throws Exception {
        // Neo4j: Ada 5, Bob 2, Carl 5 — Dana knows it at 5 too but is soft-deleted.
        mvc.perform(get("/api/v1/experts/coverage")
                .param("skills", neo4jSkill)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skill").value(neo4jSkill))
                .andExpect(jsonPath("$[0].knownBy").value(3))
                .andExpect(jsonPath("$[0].experts").value(contains("ada Lovelace", "carl Cache")));

        // Docker: Ada 3 and Bob 1 — known, but nobody is at the go-to level.
        mvc.perform(get("/api/v1/experts/coverage")
                .param("skills", dockerSkill)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].knownBy").value(2))
                .andExpect(jsonPath("$[0].experts").isEmpty());
    }

    @Test
    void coverage_reportsSkillsNobodyKnows() throws Exception {
        String unknown = "cobol-" + UUID.randomUUID();

        mvc.perform(get("/api/v1/experts/coverage")
                .param("skills", unknown)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].knownBy").value(0))
                .andExpect(jsonPath("$[0].experts").isEmpty());
    }

    @Test
    void softDeletedPerson_neverSurfaces() throws Exception {
        mvc.perform(get("/api/v1/experts")
                .param("skills", neo4jSkill + "," + dockerSkill)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + danaId + "')]").doesNotExist());
    }

    @Test
    void skillNamesAndTeamFilter_areCaseInsensitive() throws Exception {
        mvc.perform(get("/api/v1/experts")
                .param("skills", neo4jSkill.toUpperCase() + "," + dockerSkill.toUpperCase())
                .param("team", teamName.toLowerCase())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(adaId));
    }

    @Test
    void teamFilter_excludesNonMembers() throws Exception {
        mvc.perform(get("/api/v1/experts")
                .param("skills", neo4jSkill + "," + dockerSkill)
                .param("team", "no-such-team")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void noMatch_is200WithEmptyList() throws Exception {
        mvc.perform(get("/api/v1/experts")
                .param("skills", "cobol-" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void cypherInjection_throughSkillsParam_deletesNothing() throws Exception {
        long peopleBefore = countPeople();

        // The classic payload. Because $skillNames is a parameter it is just a skill name nobody has.
        mvc.perform(get("/api/v1/experts")
                .param("skills", "React'}) DETACH DELETE (n) //")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        assertThat(countPeople()).isEqualTo(peopleBefore);
        // And the graph still answers normally afterwards.
        mvc.perform(get("/api/v1/experts")
                .param("skills", neo4jSkill + "," + dockerSkill)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void missingSkillsParam_isBadRequest() throws Exception {
        mvc.perform(get("/api/v1/experts")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noToken_isUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/experts").param("skills", neo4jSkill))
                .andExpect(status().isUnauthorized());
    }

    private long countPeople() {
        return neo4jClient.query("MATCH (p:Person) RETURN count(p) AS total")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("total").asLong())
                .one()
                .orElse(0L);
    }
}
