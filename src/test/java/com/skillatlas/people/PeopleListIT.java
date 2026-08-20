package com.skillatlas.people;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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

/** Fixtures are UUID-suffixed and torn down again — the database may be someone's dev instance. */
class PeopleListIT extends AbstractNeo4jIT {

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

    String adaId;
    String danaId;
    String teamName;
    String teamId;
    List<String> skillIds;
    String token;

    @BeforeEach
    void seed() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        teamName = "Backend-" + u;

        // Four skills at four levels: the row must show the top three, strongest first.
        Skill one = skillsService.create(new SkillCreateRequest("Neo4j-" + u, SkillCategory.DATABASE, "#4581C3"));
        Skill two = skillsService.create(new SkillCreateRequest("Docker-" + u, SkillCategory.TOOL, "#2496ED"));
        Skill three = skillsService.create(new SkillCreateRequest("Java-" + u, SkillCategory.LANGUAGE, "#E76F00"));
        Skill four = skillsService.create(new SkillCreateRequest("SQL-" + u, SkillCategory.LANGUAGE, "#CC2927"));
        skillIds = List.of(one.getId(), two.getId(), three.getId(), four.getId());

        adaId = createPerson("ada", u, "Lovelace");
        danaId = createPerson("dana", u, "Delete");

        peopleSkillsService.setSkillLevel(adaId, one.getId(), 5);
        peopleSkillsService.setSkillLevel(adaId, two.getId(), 4);
        peopleSkillsService.setSkillLevel(adaId, three.getId(), 3);
        peopleSkillsService.setSkillLevel(adaId, four.getId(), 2);

        Team team = teamsService.create(new TeamCreateRequest(teamName));
        teamId = team.getId();
        teamsService.addMember(teamId, adaId);

        peopleService.softDelete(danaId);

        token = jwtService.issue(adaId, Role.MEMBER);
    }

    @AfterEach
    void cleanup() {
        neo4jClient.query("MATCH (n) WHERE n.id IN $ids DETACH DELETE n")
                .bindAll(Map.of("ids", List.of(adaId, danaId, teamId, skillIds.get(0),
                        skillIds.get(1), skillIds.get(2), skillIds.get(3))))
                .run();
    }

    private void expectRows(MockHttpServletRequestBuilder request, String id, boolean present)
            throws Exception {
        var result = mvc.perform(request.param("size", "100")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        String row = "$.content[?(@.id == '" + id + "')]";
        result.andExpect(present ? jsonPath(row).exists() : jsonPath(row).doesNotExist());
    }

    private void wipe(String id) {
        neo4jClient.query("MATCH (n) WHERE n.id = $id DETACH DELETE n")
                .bindAll(Map.of("id", id))
                .run();
    }

    private long countNodes() {
        return neo4jClient.query("MATCH (n) RETURN count(n) AS total")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("total").asLong())
                .one()
                .orElse(0L);
    }

    private String skillName(String skillId) {
        return skillsService.getById(skillId).getName();
    }

    private String createPerson(String first, String suffix, String last) {
        Person person = peopleService.create(new PersonCreateRequest(
                first + "-" + suffix + "@test.com", "Password123!", first, last, "Engineer", null,
                Role.MEMBER));
        return person.getId();
    }

    @Test
    void row_carriesTeamsAndTheStrongestThreeSkills() throws Exception {
        String row = "$.content[?(@.id == '" + adaId + "')]";

        mvc.perform(get("/api/v1/people").param("size", "100")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath(row + ".teams[0]").value(teamName))
                // Four skills known, three shown, level descending — the 2 falls off.
                .andExpect(jsonPath(row + ".topSkills.length()").value(3))
                .andExpect(jsonPath(row + ".topSkills[0].level").value(5))
                .andExpect(jsonPath(row + ".topSkills[1].level").value(4))
                .andExpect(jsonPath(row + ".topSkills[2].level").value(3))
                .andExpect(jsonPath(row + ".topSkills[0].skillId").value(skillIds.get(0)))
                // Still a list shape, not the profile: no relationship graph, no secrets.
                .andExpect(jsonPath(row + ".neighbourhood").doesNotExist())
                .andExpect(jsonPath(row + ".passwordHash").doesNotExist());
    }

    @Test
    void personWithoutSkillsOrTeam_getsEmptyListsNotNulls() throws Exception {
        String plain = createPerson("plain", UUID.randomUUID().toString().substring(0, 8), "Person");
        try {
            mvc.perform(get("/api/v1/people").param("size", "100")
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.id == '" + plain + "')].teams").exists())
                    .andExpect(jsonPath("$.content[?(@.id == '" + plain + "')].topSkills[0]")
                            .doesNotExist());
        } finally {
            neo4jClient.query("MATCH (n) WHERE n.id = $id DETACH DELETE n")
                    .bindAll(Map.of("id", plain))
                    .run();
        }
    }

    @Test
    void searchFilter_matchesNameAndEmail_ignoringCase() throws Exception {
        expectRows(get("/api/v1/people").param("search", "LOVELACE"), adaId, true);
        expectRows(get("/api/v1/people").param("search", "ada-"), adaId, true);
        expectRows(get("/api/v1/people").param("search", "nobody-by-that-name"), adaId, false);
    }

    @Test
    void teamFilter_keepsOnlyMembers() throws Exception {
        String outsider = createPerson("outsider", UUID.randomUUID().toString().substring(0, 8), "Person");
        try {
            expectRows(get("/api/v1/people").param("team", teamName.toUpperCase()), adaId, true);
            expectRows(get("/api/v1/people").param("team", teamName), outsider, false);
        } finally {
            wipe(outsider);
        }
    }

    @Test
    void skillFilter_keepsOnlyPeopleWhoKnowIt() throws Exception {
        String unskilled = createPerson("unskilled", UUID.randomUUID().toString().substring(0, 8), "Person");
        try {
            String skill = skillName(skillIds.get(0));
            expectRows(get("/api/v1/people").param("skill", skill), adaId, true);
            expectRows(get("/api/v1/people").param("skill", skill), unskilled, false);
        } finally {
            wipe(unskilled);
        }
    }

    @Test
    void mostRecentlyAdded_sortsFirst_thenAlphabetically() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String older = createPerson("aaa", suffix, "Aaa");
        String newer = createPerson("zzz", suffix, "Zzz");
        try {
            // Alphabetically Zzz trails Aaa; it leads because it was added last.
            mvc.perform(get("/api/v1/people").param("search", suffix).param("size", "100")
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].id").value(newer))
                    .andExpect(jsonPath("$.content[1].id").value(older));
        } finally {
            wipe(older);
            wipe(newer);
        }
    }

    @Test
    void cypherInjection_throughSearchTeamAndSkill_deletesNothing() throws Exception {
        String payload = "React'}) DETACH DELETE (n) //";
        long before = countNodes();

        for (String filter : List.of("search", "team", "skill")) {
            mvc.perform(get("/api/v1/people").param(filter, payload)
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }

        // An empty page alone would also be true of a wiped database — the count is the assertion.
        assertThat(countNodes()).isEqualTo(before);
    }

    @Test
    void softDeletedPerson_isNotInTheList() throws Exception {
        mvc.perform(get("/api/v1/people").param("size", "100")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + danaId + "')]").doesNotExist());
    }
}
