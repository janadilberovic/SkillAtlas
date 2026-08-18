package com.skillatlas.dashboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import com.skillatlas.dashboard.dto.DashboardResponse;

/**
 * The only place dashboard Cypher lives: four aggregate queries, each answering one widget in one
 * round trip. No query runs per team, per skill or per person — the whole point of the screen is
 * the shape of the company, and asking per row would not survive a real headcount.
 */
@Repository
public class DashboardRepository {

    private static final String METRICS = """
            RETURN count { MATCH (p:Person) WHERE p.isDeleted = false } AS people,
                   count { MATCH (s:Skill) } AS skills,
                   count { MATCH (pr:Project) } AS projects,
                   count { MATCH (a:Person)-[:MENTORS]->(b:Person)
                           WHERE a.isDeleted = false AND b.isDeleted = false } AS mentorships
            """;

    // The gap the spec asks for: a technology the team's projects actually use, that the team
    // itself barely knows. The COUNT subquery counts members of *that* team who know *that* skill,
    // so a company-wide expert outside the team does not close a team's gap - which is the point.
    private static final String SKILL_GAP = """
            MATCH (t:Team)<-[:MEMBER_OF]-(p:Person)-[:WORKED_ON]->(pr:Project)-[:USES]->(s:Skill)
            WHERE p.isDeleted = false
            WITH t, s, collect(DISTINCT pr.name) AS projects
            WITH t, s, projects,
                 count { MATCH (m:Person)-[:MEMBER_OF]->(t)
                         MATCH (m)-[:KNOWS]->(s)
                         WHERE m.isDeleted = false } AS knownBy
            WHERE knownBy <= $threshold
            RETURN t.name AS team, s.name AS skill, projects, knownBy
            ORDER BY knownBy ASC, team ASC, skill ASC
            LIMIT $limit
            """;

    private static final String BUS_FACTOR = """
            MATCH (p:Person)-[:KNOWS]->(s:Skill)
            WHERE p.isDeleted = false
            WITH s, collect(p) AS knowers
            WHERE size(knowers) = 1
            WITH s, knowers[0] AS only
            RETURN s.name AS skill, only.id AS personId,
                   only.firstName + ' ' + only.lastName AS personName
            ORDER BY skill ASC
            LIMIT $limit
            """;

    // The list is capped but the count is not: "12 waiting" with five names beats five names and
    // no idea how deep the queue goes.
    private static final String MAPPING_QUEUE = """
            MATCH (p:Person)
            WHERE p.isDeleted = false AND NOT EXISTS { MATCH (p)-[:KNOWS]->(:Skill) }
            WITH p ORDER BY p.lastName, p.firstName
            WITH collect({id: p.id, name: p.firstName + ' ' + p.lastName}) AS waiting
            RETURN size(waiting) AS total, waiting[0..$limit] AS people
            """;

    private final Neo4jClient client;

    public DashboardRepository(Neo4jClient client) {
        this.client = client;
    }

    public DashboardResponse.Metrics metrics() {
        Map<String, Object> row = client.query(METRICS).fetch().one().orElse(Map.of());
        return new DashboardResponse.Metrics(
                asLong(row.get("people")), asLong(row.get("skills")),
                asLong(row.get("projects")), asLong(row.get("mentorships")));
    }

    /** @param threshold the highest "known by" count that still counts as a gap */
    public List<DashboardResponse.SkillGapRow> skillGap(int threshold, int limit) {
        return List.copyOf(client.query(SKILL_GAP)
                .bindAll(Map.of("threshold", threshold, "limit", limit))
                .fetchAs(DashboardResponse.SkillGapRow.class)
                .mappedBy((typeSystem, record) -> new DashboardResponse.SkillGapRow(
                        record.get("team").asString(),
                        record.get("skill").asString(),
                        sorted(record.get("projects").asList(Value::asString)),
                        record.get("knownBy").asLong()))
                .all());
    }

    public List<DashboardResponse.BusFactorRow> busFactor(int limit) {
        return List.copyOf(client.query(BUS_FACTOR)
                .bind(limit).to("limit")
                .fetchAs(DashboardResponse.BusFactorRow.class)
                .mappedBy((typeSystem, record) -> new DashboardResponse.BusFactorRow(
                        record.get("skill").asString(),
                        record.get("personId").asString(),
                        record.get("personName").asString()))
                .all());
    }

    public DashboardResponse.MappingQueue mappingQueue(int limit) {
        return client.query(MAPPING_QUEUE)
                .bind(limit).to("limit")
                .fetchAs(DashboardResponse.MappingQueue.class)
                .mappedBy((typeSystem, record) -> new DashboardResponse.MappingQueue(
                        record.get("total").asLong(),
                        record.get("people").asList(v -> new DashboardResponse.PersonRef(
                                v.get("id").asString(), v.get("name").asString()))))
                .one()
                .orElse(new DashboardResponse.MappingQueue(0, List.of()));
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static List<String> sorted(List<String> values) {
        List<String> copy = new ArrayList<>(values);
        copy.sort(Comparator.naturalOrder());
        return List.copyOf(copy);
    }
}
