package com.skillatlas.projects;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.skillatlas.projects.domain.Project;
import com.skillatlas.projects.dto.ProjectCreateRequest;
import com.skillatlas.projects.dto.ProjectMemberRequest;
import com.skillatlas.security.JwtService;
import com.skillatlas.skills.SkillsService;
import com.skillatlas.skills.domain.Skill;
import com.skillatlas.skills.dto.SkillCreateRequest;
import com.skillatlas.skills.enums.SkillCategory;
import com.skillatlas.support.AbstractNeo4jIT;

// The project page: its roster, the head count on a list card, and editing what the project USES.
class ProjectDetailIT extends AbstractNeo4jIT {

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
    String neoId;
    String dockerId;
    String adminId;
    String stayerId;
    String leaverId;
    String projectId;
    String adminToken;

    @BeforeEach
    void seed() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        Skill neo = skillsService.create(new SkillCreateRequest("Neo4j-" + suffix, SkillCategory.DATABASE, null));
        Skill docker = skillsService.create(new SkillCreateRequest("Docker-" + suffix, SkillCategory.TOOL, null));
        neoId = neo.getId();
        dockerId = docker.getId();

        Person admin = peopleService.create(new PersonCreateRequest(
                "admin-" + suffix + "@test.com", "Password123!", "Site", "Admin", "Admin", null, Role.ADMIN));
        Person stayer = peopleService.create(new PersonCreateRequest(
                "stayer-" + suffix + "@test.com", "Password123!", "Ada", "Stayer", "Engineer", null, Role.MEMBER));
        Person leaver = peopleService.create(new PersonCreateRequest(
                "leaver-" + suffix + "@test.com", "Password123!", "Carl", "Leaver", "Engineer", null, Role.MEMBER));
        adminId = admin.getId();
        stayerId = stayer.getId();
        leaverId = leaver.getId();
        adminToken = jwtService.issue(adminId, Role.ADMIN);

        Project project = projectsService.create(new ProjectCreateRequest(
                "Meridian-" + suffix, "Staffing recommendations",
                LocalDate.parse("2026-01-01"), null, Set.of(neoId)));
        projectId = project.getId();
        projectsService.assignMember(projectId, stayerId,
                new ProjectMemberRequest("Backend dev", LocalDate.parse("2026-01-01"), null));
        projectsService.assignMember(projectId, leaverId,
                new ProjectMemberRequest("Data engineer", LocalDate.parse("2026-01-01"),
                        LocalDate.parse("2026-04-30")));

        // Ada knows one skill the project uses and one it does not; only the first belongs on the page.
        peopleSkillsService.setSkillLevel(stayerId, neoId, 4);
        peopleSkillsService.setSkillLevel(stayerId, dockerId, 5);
        peopleService.softDelete(leaverId);
    }

    @AfterEach
    void cleanup() {
        neo4jClient.query("MATCH (n) WHERE n.id IN $ids DETACH DELETE n")
                .bindAll(Map.of("ids", List.of(neoId, dockerId, adminId, stayerId, leaverId, projectId)))
                .run();
    }

    @Test
    void detail_listsTheRosterWithRolesAndPeriods() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].name").value("Ada Stayer"))
                .andExpect(jsonPath("$.members[0].role").value("Backend dev"))
                .andExpect(jsonPath("$.members[0].from").value("2026-01-01"))
                .andExpect(jsonPath("$.members[0].to").doesNotExist());
    }

    // Spec §4.6: the WORKED_ON history survives a soft delete, flagged, and stops being a head count.
    @Test
    void detail_keepsSoftDeletedMemberFlaggedAndOutOfTheCount() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.members[1].name").value("Carl Leaver"))
                .andExpect(jsonPath("$.members[1].left").value(true))
                .andExpect(jsonPath("$.members[0].left").value(false));
    }

    // The subgraph draws KNOWS edges only inside the project's own stack.
    @Test
    void detail_carriesOnlyTheStackSkillsAMemberKnows() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].knows.length()").value(1))
                .andExpect(jsonPath("$.members[0].knows[0].skillId").value(neoId))
                .andExpect(jsonPath("$.members[0].knows[0].level").value(4))
                .andExpect(jsonPath("$.members[1].knows.length()").value(0));
    }

    @Test
    void list_carriesTheLiveHeadCountWithoutTheRoster() throws Exception {
        mvc.perform(get("/api/v1/projects")
                .param("search", "Meridian-" + suffix)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].memberCount").value(1))
                .andExpect(jsonPath("$.content[0].members").doesNotExist());
    }

    // Adding a technology is a full PUT, and the answer has to carry the roster back or the
    // project screen would blank the people it is already showing.
    @Test
    void update_addsAUsesEdgeAndAnswersWithTheRoster() throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON)
                .content("""
                        {"name":"Meridian-%s","description":"Staffing recommendations",
                         "startDate":"2026-01-01","active":true,"skillIds":["%s","%s"]}
                        """.formatted(suffix, neoId, dockerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills.length()").value(2))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].knows.length()").value(2));
    }
}
