package com.skillatlas.projects;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import com.skillatlas.projects.dto.ProjectMemberResponse;
import com.skillatlas.projects.dto.ProjectMemberResponse.StackSkill;

/**
 * The roster behind a project. WORKED_ON is owned by {@code Person}, so it is a projection rather
 * than a field on the {@code Project} entity.
 *
 * <p>{@link Neo4jClient} rather than a derived repository, because a row spans three relationship
 * types and is not a {@code Project}.
 */
@Repository
public class ProjectMembersRepository {

    // Soft-deleted people are kept here on purpose (spec §4.6) and flagged, not filtered.
    // $id is bound, never concatenated: React'}) DETACH DELETE (n) // is an id that matches nobody.
    private static final String MEMBERS = """
            MATCH (person:Person)-[w:WORKED_ON]->(project:Project {id: $id})
            RETURN person.id AS personId,
                   person.firstName + ' ' + person.lastName AS name,
                   person.isDeleted AS gone,
                   w.role AS role, w.from AS fromDate, w.to AS toDate,
                   COLLECT {
                     MATCH (person)-[k:KNOWS]->(s:Skill)<-[:USES]-(project)
                     RETURN {skillId: s.id, name: s.name, level: k.level}
                   } AS knows
            """;

    // One round trip for a whole page of cards: counting per project would be the N+1 the rules ban.
    // Projects nobody staffed return no row at all, which the caller reads as zero.
    private static final String COUNTS = """
            UNWIND $ids AS projectId
            MATCH (person:Person)-[:WORKED_ON]->(:Project {id: projectId})
            WHERE person.isDeleted = false
            RETURN projectId, count(person) AS members
            """;

    private final Neo4jClient client;

    public ProjectMembersRepository(Neo4jClient client) {
        this.client = client;
    }

    /** Staff first, then people who left; alphabetical within each. */
    public List<ProjectMemberResponse> findMembers(String projectId) {
        return client.query(MEMBERS)
                .bind(projectId).to("id")
                .fetchAs(ProjectMemberResponse.class)
                .mappedBy((typeSystem, record) -> new ProjectMemberResponse(
                        record.get("personId").asString(),
                        record.get("name").asString(),
                        record.get("role").asString(null),
                        date(record.get("fromDate")),
                        date(record.get("toDate")),
                        record.get("gone").asBoolean(false),
                        record.get("knows").asList(ProjectMembersRepository::stackSkill).stream()
                                .sorted(Comparator.comparingInt(StackSkill::level).reversed()
                                        .thenComparing(StackSkill::name))
                                .toList()))
                .all()
                .stream()
                .sorted(Comparator.comparing(ProjectMemberResponse::left)
                        .thenComparing(ProjectMemberResponse::name))
                .toList();
    }

    /** Live staff per project id; ids with nobody on them are absent from the map. */
    public Map<String, Integer> countByProject(List<String> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> counts = new HashMap<>();
        client.query(COUNTS)
                .bind(projectIds).to("ids")
                .fetch()
                .all()
                .forEach(row -> counts.put(
                        (String) row.get("projectId"),
                        ((Number) row.get("members")).intValue()));
        return counts;
    }

    private static StackSkill stackSkill(Value v) {
        return new StackSkill(
                v.get("skillId").asString(),
                v.get("name").asString(),
                v.get("level").asInt());
    }

    private static LocalDate date(Value v) {
        return v.isNull() ? null : v.asLocalDate();
    }
}
