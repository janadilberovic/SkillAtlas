package com.skillatlas.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.skillatlas.skills.domain.Skill;
import com.skillatlas.skills.dto.SkillCreateRequest;
import com.skillatlas.skills.enums.SkillCategory;
import com.skillatlas.support.AbstractNeo4jIT;

/** Fixtures are UUID-suffixed and torn down again — the database may be someone's dev instance. */
class SkillsCatalogIT extends AbstractNeo4jIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    SkillsService skillsService;
    @Autowired
    PeopleService peopleService;
    @Autowired
    PeopleSkillsService peopleSkillsService;
    @Autowired
    ProjectsService projectsService;
    @Autowired
    JwtService jwtService;
    @Autowired
    Neo4jClient neo4jClient;

    String suffix;
    String graphSkillId;
    String quietSkillId;
    String adaId;
    String danaId;
    String projectId;
    String token;

    @BeforeEach
    void seed() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        Skill graph = skillsService.create(
                new SkillCreateRequest("Neo4j-" + suffix, SkillCategory.DATABASE, "#4581C3"));
        Skill quiet = skillsService.create(
                new SkillCreateRequest("Fortran-" + suffix, SkillCategory.LANGUAGE, "#734F96"));
        graphSkillId = graph.getId();
        quietSkillId = quiet.getId();

        adaId = createPerson("ada", "Lovelace");
        danaId = createPerson("dana", "Delete");

        peopleSkillsService.setSkillLevel(adaId, graphSkillId, 5);
        peopleSkillsService.addWish(adaId, quietSkillId);
        // Dana knows and wants the same two, then goes: her edges must stop counting.
        peopleSkillsService.setSkillLevel(danaId, graphSkillId, 3);
        peopleSkillsService.addWish(danaId, quietSkillId);
        peopleService.softDelete(danaId);

        Project project = projectsService.create(new ProjectCreateRequest(
                "Atlas-" + suffix, null, null, null, Set.of(graphSkillId)));
        projectId = project.getId();

        token = jwtService.issue(adaId, Role.MEMBER);
    }

    @AfterEach
    void cleanup() {
        neo4jClient.query("MATCH (n) WHERE n.id IN $ids DETACH DELETE n")
                .bindAll(Map.of("ids", List.of(adaId, danaId, projectId, graphSkillId, quietSkillId)))
                .run();
    }

    private String createPerson(String first, String last) {
        Person person = peopleService.create(new PersonCreateRequest(
                first + "-" + suffix + "@test.com", "Password123!", first, last, "Engineer", null,
                Role.MEMBER));
        return person.getId();
    }

    private long countNodes() {
        return neo4jClient.query("MATCH (n) RETURN count(n) AS total")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("total").asLong())
                .one()
                .orElse(0L);
    }

    @Test
    void row_carriesCountsAndTheProjectsUsingIt() throws Exception {
        String row = "$.content[?(@.id == '" + graphSkillId + "')]";

        mvc.perform(get("/api/v1/skills").param("search", suffix).param("size", "100")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                // Ada knows it, Dana is soft-deleted — her KNOWS must not be counted.
                .andExpect(jsonPath(row + ".knownBy").value(1))
                .andExpect(jsonPath(row + ".wantedBy").value(0))
                .andExpect(jsonPath(row + ".usedBy[0]").value("Atlas-" + suffix));
    }

    @Test
    void softDeletedPerson_countsInNeitherKnownByNorWantedBy() throws Exception {
        String row = "$.content[?(@.id == '" + quietSkillId + "')]";

        mvc.perform(get("/api/v1/skills").param("search", suffix).param("size", "100")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath(row + ".knownBy").value(0))
                // Ada wants it; Dana wanted it too and was deleted.
                .andExpect(jsonPath(row + ".wantedBy").value(1))
                .andExpect(jsonPath(row + ".usedBy[0]").doesNotExist());
    }

    @Test
    void searchFilter_matchesNameFragment_ignoringCase() throws Exception {
        mvc.perform(get("/api/v1/skills").param("search", "NEO4J-" + suffix.toUpperCase())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(graphSkillId));
    }

    @Test
    void categoryFilter_keepsOnlyThatCategory() throws Exception {
        mvc.perform(get("/api/v1/skills").param("search", suffix).param("category", "database")
                .param("size", "100")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(graphSkillId));
    }

    @Test
    void unknownCategory_is400_notAServerError() throws Exception {
        mvc.perform(get("/api/v1/skills").param("category", "PHILOSOPHY")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sortWanted_putsTheMostWishedForFirst() throws Exception {
        // Alphabetically Fortran leads, so the ranking only proves itself if Neo4j has the wishes.
        // Ada cannot supply one: she knows it at 5, and a mastered skill is not a wish.
        String bobId = createPerson("bob", "Wisher");
        String calId = createPerson("cal", "Wisher");
        try {
            peopleSkillsService.addWish(bobId, graphSkillId);
            peopleSkillsService.addWish(calId, graphSkillId);

            mvc.perform(get("/api/v1/skills").param("search", suffix).param("sort", "wanted")
                    .param("size", "100")
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(graphSkillId))
                    .andExpect(jsonPath("$.content[0].wantedBy").value(2))
                    .andExpect(jsonPath("$.content[1].id").value(quietSkillId));
        } finally {
            neo4jClient.query("MATCH (n) WHERE n.id IN $ids DETACH DELETE n")
                    .bindAll(Map.of("ids", List.of(bobId, calId)))
                    .run();
        }
    }

    @Test
    void sortKnown_putsTheThinnestCoverageFirst() throws Exception {
        // Alphabetically Ada-lang leads and nobody-knows-it Fortran trails, so a default ordering
        // that leaked through would put Ada-lang first and fail here.
        Skill known = skillsService.create(
                new SkillCreateRequest("Ada-lang-" + suffix, SkillCategory.LANGUAGE, "#02569B"));
        try {
            peopleSkillsService.setSkillLevel(adaId, known.getId(), 4);

            mvc.perform(get("/api/v1/skills").param("search", suffix).param("sort", "known")
                    .param("size", "100")
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(quietSkillId))
                    .andExpect(jsonPath("$.content[0].knownBy").value(0))
                    // Neo4j and Ada-lang both sit at 1 with no wishes, so the name breaks the tie.
                    .andExpect(jsonPath("$.content[1].id").value(known.getId()))
                    .andExpect(jsonPath("$.content[2].id").value(graphSkillId));
        } finally {
            neo4jClient.query("MATCH (n) WHERE n.id = $id DETACH DELETE n")
                    .bindAll(Map.of("id", known.getId()))
                    .run();
        }
    }

    @Test
    void paging_reportsTheFullTotal_notThePageSize() throws Exception {
        mvc.perform(get("/api/v1/skills").param("search", suffix).param("size", "1")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                // Alphabetical by default: Fortran before Neo4j.
                .andExpect(jsonPath("$.content[0].id").value(quietSkillId));
    }

    @Test
    void cypherInjection_throughSearch_deletesNothing() throws Exception {
        long before = countNodes();

        mvc.perform(get("/api/v1/skills").param("search", "React'}) DETACH DELETE (n) //")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        // An empty page alone would also be true of a wiped database — the count is the assertion.
        assertThat(countNodes()).isEqualTo(before);
    }
}
