package com.skillatlas.teams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.skillatlas.support.AbstractNeo4jIT;
import com.skillatlas.teams.domain.Team;
import com.skillatlas.teams.dto.TeamCreateRequest;

// Putting people in a team (MEMBER_OF): who may write it, and that repeating it stays one edge.
class TeamMembersIT extends AbstractNeo4jIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    TeamsService teamsService;
    @Autowired
    PeopleService peopleService;
    @Autowired
    JwtService jwtService;
    @Autowired
    Neo4jClient neo4jClient;

    String suffix;
    String teamId;
    String adminId;
    String memberId;
    String adminToken;
    String memberToken;

    @BeforeEach
    void seed() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        Team team = teamsService.create(new TeamCreateRequest("Platform-" + suffix));
        Person admin = peopleService.create(new PersonCreateRequest(
                "admin-" + suffix + "@test.com", "Password123!", "Site", "Admin", "Admin", null, Role.ADMIN));
        Person member = peopleService.create(new PersonCreateRequest(
                "member-" + suffix + "@test.com", "Password123!", "Mia", "Member", "Engineer", null, Role.MEMBER));
        teamId = team.getId();
        adminId = admin.getId();
        memberId = member.getId();
        adminToken = jwtService.issue(adminId, Role.ADMIN);
        memberToken = jwtService.issue(memberId, Role.MEMBER);
    }

    // The suite runs against a real, possibly shared database — leave nothing behind.
    @AfterEach
    void cleanup() {
        neo4jClient.query("MATCH (n) WHERE n.id IN $ids DETACH DELETE n")
                .bindAll(Map.of("ids", List.of(teamId, adminId, memberId)))
                .run();
    }

    private long edgeCount() {
        return neo4jClient.query("MATCH (:Person {id: $personId})-[r:MEMBER_OF]->(:Team {id: $teamId}) RETURN count(r)")
                .bindAll(Map.of("personId", memberId, "teamId", teamId))
                .fetchAs(Long.class)
                .one()
                .orElse(0L);
    }

    @Test
    void addMember_asAdmin_returns204AndCreatesTheEdge() throws Exception {
        mvc.perform(post("/api/v1/teams/{id}/members/{personId}", teamId, memberId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(edgeCount()).isEqualTo(1);
    }

    // The repository MERGEs, so the modal re-adding someone already in the team is a no-op.
    @Test
    void addMember_twice_leavesOneEdge() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/teams/{id}/members/{personId}", teamId, memberId)
                    .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNoContent());
        }

        assertThat(edgeCount()).isEqualTo(1);
    }

    @Test
    void addMember_asMember_isForbidden() throws Exception {
        mvc.perform(post("/api/v1/teams/{id}/members/{personId}", teamId, memberId)
                .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());

        assertThat(edgeCount()).isZero();
    }

    @Test
    void addMember_toUnknownTeam_returns404() throws Exception {
        mvc.perform(post("/api/v1/teams/{id}/members/{personId}", "no-such-team", memberId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void addMember_withSoftDeletedPerson_returns404() throws Exception {
        peopleService.softDelete(memberId);

        mvc.perform(post("/api/v1/teams/{id}/members/{personId}", teamId, memberId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        assertThat(edgeCount()).isZero();
    }
}
