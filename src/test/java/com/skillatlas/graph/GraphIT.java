package com.skillatlas.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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

import com.skillatlas.people.PeopleService;
import com.skillatlas.people.PeopleSkillsService;
import com.skillatlas.people.domain.Person;
import com.skillatlas.people.dto.PersonCreateRequest;
import com.skillatlas.people.enums.Role;
import com.skillatlas.projects.ProjectsService;
import com.skillatlas.projects.domain.Project;
import com.skillatlas.projects.dto.ProjectCreateRequest;
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
 * Integration tests for E5.1 against a real Neo4j: the type and team filters, the mandatory cap,
 * soft-delete exclusion and the Cypher-injection case.
 *
 * <p>The database is shared, so nothing here asserts an absolute node count — the fixture carries a
 * per-run UUID suffix, assertions look for its own ids, and {@link #cleanup()} removes exactly what
 * it created.
 */
class GraphIT extends AbstractNeo4jIT {

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

    String teamName;
    String teamId;
    String neo4jSkillId;
    String dockerSkillId;
    String projectId;
    String adaId;
    String bobId;
    String danaId;
    String token;

    @BeforeEach
    void seed() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        teamName = "Graph-" + u;

        Skill neo4j = skillsService.create(
                new SkillCreateRequest("GraphNeo4j-" + u, SkillCategory.DATABASE, "#4581C3"));
        Skill docker = skillsService.create(
                new SkillCreateRequest("GraphDocker-" + u, SkillCategory.TOOL, "#2496ED"));
        neo4jSkillId = neo4j.getId();
        dockerSkillId = docker.getId();

        adaId = createPerson("gada", u, "Lovelace");
        bobId = createPerson("gbob", u, "Byte");
        danaId = createPerson("gdana", u, "Delete");

        peopleSkillsService.setSkillLevel(adaId, neo4jSkillId, 5);
        peopleSkillsService.addWish(adaId, dockerSkillId);
        peopleSkillsService.setSkillLevel(bobId, neo4jSkillId, 2);
        // Dana knows the same skill but is soft-deleted: she must not appear as a node, and the
        // MENTORS edge from Ada must not smuggle her in either.
        peopleSkillsService.setSkillLevel(danaId, neo4jSkillId, 4);

        Team team = teamsService.create(new TeamCreateRequest(teamName));
        teamId = team.getId();
        Project project = projectsService.create(new ProjectCreateRequest("GraphProject-" + u,
                "Explorer fixture", LocalDate.of(2025, 1, 1), null, Set.of(neo4jSkillId)));
        projectId = project.getId();

        // No write endpoints exist for these edges yet, so the fixture wires them directly.
        // Separate MATCH clauses per pattern: one clause with disconnected patterns triggers
        // Neo4j's CartesianProduct notification and spams the test log.
        run("MATCH (p:Person {id: $a}) MATCH (t:Team {id: $t}) MERGE (p)-[:MEMBER_OF]->(t)",
                Map.of("a", adaId, "t", teamId));
        run("MATCH (p:Person {id: $a}) MATCH (pr:Project {id: $pr}) "
                + "MERGE (p)-[:WORKED_ON {role: 'Dev', from: $from}]->(pr)",
                Map.of("a", adaId, "pr", projectId, "from", LocalDate.of(2025, 1, 1)));
        run("MATCH (a:Person {id: $a}) MATCH (d:Person {id: $d}) "
                + "MERGE (a)-[:MENTORS {skillId: $s}]->(d)",
                Map.of("a", adaId, "d", danaId, "s", neo4jSkillId));

        peopleService.softDelete(danaId);
        token = jwtService.issue(adaId, Role.MEMBER);
    }

    @AfterEach
    void cleanup() {
        run("MATCH (n) WHERE n.id IN $ids DETACH DELETE n", Map.of("ids",
                List.of(adaId, bobId, danaId, neo4jSkillId, dockerSkillId, teamId, projectId)));
    }

    private String createPerson(String first, String suffix, String last) {
        Person person = peopleService.create(new PersonCreateRequest(
                first + "-" + suffix + "@test.com", "Password123!", first, last, "Engineer", null,
                Role.MEMBER));
        return person.getId();
    }

    private void run(String cypher, Map<String, Object> params) {
        neo4jClient.query(cypher).bindAll(params).run();
    }

    @Test
    void teamFilter_returnsThatTeamsPersonAndWhatSheTouches() throws Exception {
        mvc.perform(get("/api/v1/graph").param("team", teamName).param("limit", "200")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[?(@.id == '" + adaId + "')]").exists())
                .andExpect(jsonPath("$.nodes[?(@.id == '" + neo4jSkillId + "')]").exists())
                .andExpect(jsonPath("$.nodes[?(@.id == '" + projectId + "')]").exists())
                .andExpect(jsonPath("$.nodes[?(@.id == '" + teamId + "')]").exists())
                // Bob is in no team, so the filter must leave him out.
                .andExpect(jsonPath("$.nodes[?(@.id == '" + bobId + "')]").doesNotExist())
                .andExpect(jsonPath("$.edges[?(@.source == '" + adaId + "' && @.target == '"
                        + neo4jSkillId + "' && @.type == 'KNOWS')]").exists())
                .andExpect(jsonPath("$.edges[?(@.source == '" + adaId + "' && @.target == '"
                        + dockerSkillId + "' && @.type == 'WANTS_TO_LEARN')]").exists())
                .andExpect(jsonPath("$.edges[?(@.source == '" + projectId + "' && @.target == '"
                        + neo4jSkillId + "' && @.type == 'USES')]").exists());
    }

    @Test
    void teamFilterIsCaseInsensitive() throws Exception {
        mvc.perform(get("/api/v1/graph").param("team", teamName.toUpperCase())
                .param("limit", "200").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[?(@.id == '" + adaId + "')]").exists());
    }

    @Test
    void softDeletedPerson_isNeitherNodeNorEdgeEndpoint() throws Exception {
        mvc.perform(get("/api/v1/graph").param("team", teamName).param("limit", "200")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[?(@.id == '" + danaId + "')]").doesNotExist())
                .andExpect(jsonPath("$.edges[?(@.target == '" + danaId + "')]").doesNotExist())
                .andExpect(jsonPath("$.edges[?(@.source == '" + danaId + "')]").doesNotExist());
    }

    @Test
    void softDeletedRoot_isAnEmptyGraphNotAPeek() throws Exception {
        mvc.perform(get("/api/v1/graph").param("rootId", danaId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isEmpty())
                .andExpect(jsonPath("$.edges").isEmpty());
    }

    @Test
    void typeFilter_dropsTheKindsNotAskedFor() throws Exception {
        mvc.perform(get("/api/v1/graph").param("team", teamName).param("types", "person,skill")
                .param("limit", "200").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[?(@.id == '" + neo4jSkillId + "')]").exists())
                .andExpect(jsonPath("$.nodes[?(@.id == '" + projectId + "')]").doesNotExist())
                .andExpect(jsonPath("$.nodes[?(@.id == '" + teamId + "')]").doesNotExist())
                .andExpect(jsonPath("$.edges[?(@.type == 'WORKED_ON')]").doesNotExist())
                .andExpect(jsonPath("$.edges[?(@.type == 'MEMBER_OF')]").doesNotExist());
    }

    @Test
    void filteringPeopleOut_stillLeavesTheirTeamsStanding() throws Exception {
        // Teams reach the graph only through people, so a naive both-endpoints rule would answer
        // "show me teams" with an empty canvas.
        mvc.perform(get("/api/v1/graph").param("team", teamName).param("types", "team")
                .param("limit", "200").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[?(@.id == '" + teamId + "')]").exists())
                .andExpect(jsonPath("$.nodes[?(@.id == '" + adaId + "')]").doesNotExist())
                .andExpect(jsonPath("$.edges").isEmpty());
    }

    @Test
    void projectAndSkillOnly_keepsTheUsesEdgeThatConnectsThem() throws Exception {
        mvc.perform(get("/api/v1/graph").param("team", teamName).param("types", "project,skill")
                .param("limit", "200").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edges[?(@.source == '" + projectId + "' && @.target == '"
                        + neo4jSkillId + "' && @.type == 'USES')]").exists())
                .andExpect(jsonPath("$.edges[?(@.type == 'KNOWS')]").doesNotExist());
    }

    @Test
    void unknownType_isBadRequest() throws Exception {
        mvc.perform(get("/api/v1/graph").param("types", "person,dragon")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rootedView_atOneHopIsJustTheRootsOwnEdges() throws Exception {
        mvc.perform(get("/api/v1/graph").param("rootId", adaId).param("hops", "1")
                .param("limit", "200").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[?(@.id == '" + adaId + "')]").exists())
                .andExpect(jsonPath("$.nodes[?(@.id == '" + neo4jSkillId + "')]").exists())
                // Bob knows the same skill, but that is a second hop away from Ada.
                .andExpect(jsonPath("$.nodes[?(@.id == '" + bobId + "')]").doesNotExist());
    }

    @Test
    void rootedView_atTwoHopsReachesTheColleagueSharingASkill() throws Exception {
        mvc.perform(get("/api/v1/graph").param("rootId", adaId).param("hops", "2")
                .param("limit", "200").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[?(@.id == '" + bobId + "')]").exists())
                .andExpect(jsonPath("$.edges[?(@.source == '" + bobId + "' && @.target == '"
                        + neo4jSkillId + "')]").exists());
    }

    @Test
    void missingRoot_isAnOrdinaryEmptyGraph() throws Exception {
        mvc.perform(get("/api/v1/graph").param("rootId", "no-such-person")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isEmpty())
                .andExpect(jsonPath("$.totalRelations").value(0));
    }

    @Test
    void limitCapsTheEdgesAndTruncatedSaysSo() throws Exception {
        mvc.perform(get("/api/v1/graph").param("rootId", adaId).param("hops", "1")
                .param("limit", "1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edges.length()").value(1))
                .andExpect(jsonPath("$.truncated").value(true))
                // The count is honest about what was left out, not about what fit.
                .andExpect(jsonPath("$.totalRelations").value(greaterThan(1)));
    }

    @Test
    void totalsDescribeTheCompanyAndIgnoreTheFilters() throws Exception {
        mvc.perform(get("/api/v1/graph").param("team", teamName).param("types", "team")
                .param("limit", "5").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.PERSON")
                        .value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.totals.SKILL")
                        .value(greaterThanOrEqualTo(2)));
    }

    @Test
    void cypherInjection_throughTeamAndRoot_deletesNothing() throws Exception {
        String payload = "React'}) DETACH DELETE (n) //";
        long before = countNodes();

        mvc.perform(get("/api/v1/graph").param("team", payload)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isEmpty());

        mvc.perform(get("/api/v1/graph").param("rootId", payload)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isEmpty());

        // An empty list alone would also be true of a wiped database — the count is the assertion.
        assertThat(countNodes()).isEqualTo(before);
        mvc.perform(get("/api/v1/graph").param("team", teamName).param("limit", "200")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[?(@.id == '" + adaId + "')]").exists());
    }

    @Test
    void noToken_isUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/graph"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void memberRole_isEnoughToReadTheGraph() throws Exception {
        // The graph is readable by anyone signed in (spec 02·§3) — no admin gate on this endpoint.
        mvc.perform(get("/api/v1/graph").param("limit", "5")
                .header("Authorization", "Bearer " + jwtService.issue(bobId, Role.MEMBER)))
                .andExpect(status().isOk());
    }

    private long countNodes() {
        return neo4jClient.query("MATCH (n) RETURN count(n) AS total")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("total").asLong())
                .one()
                .orElse(0L);
    }
}
