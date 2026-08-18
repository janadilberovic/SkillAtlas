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
 * The only place dashboard Cypher lives: one aggregate query per widget, each answered in one round
 * trip. No query runs per team, per skill or per person — the whole point of the screen is the
 * shape of the company, and asking per row would not survive a real headcount.
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
    private static final String SKILL_GAP_MATCH = """
            MATCH (t:Team)<-[:MEMBER_OF]-(p:Person)-[:WORKED_ON]->(pr:Project)-[:USES]->(s:Skill)
            WHERE p.isDeleted = false
            WITH t, s, collect(DISTINCT pr.name) AS projects
            WITH t, s, projects,
                 count { MATCH (t)<-[:MEMBER_OF]-(m:Person)-[:KNOWS]->(s)
                         WHERE m.isDeleted = false } AS knownBy
            WHERE knownBy <= $threshold
            """;

    // Widest gaps first, so page one is the page worth reading.
    private static final String SKILL_GAP = SKILL_GAP_MATCH + """
            RETURN t.name AS team, s.name AS skill, projects, knownBy
            ORDER BY knownBy ASC, team ASC, skill ASC
            SKIP $skip LIMIT $limit
            """;

    private static final String COUNT_SKILL_GAP = SKILL_GAP_MATCH + "RETURN count(*) AS total";

    // The admin's queue: a wish with no mentorship behind it. "No mentorship" means no *live* one -
    // a mentor who has since been deleted leaves the wish unanswered, not answered.
    // The candidate count applies the same 4.3 rule the matching endpoint does, so a row cannot
    // promise a modal that turns out empty.
    private static final String MENTOR_REQUESTS_MATCH = """
            MATCH (p:Person)-[w:WANTS_TO_LEARN]->(s:Skill)
            WHERE p.isDeleted = false
              AND NOT EXISTS {
                    MATCH (mentor:Person)-[m:MENTORS {skillId: s.id}]->(p)
                    WHERE mentor.isDeleted = false }
            """;

    // Oldest wish first: this is a queue, and the person who asked in March should not sit behind
    // the one who asked yesterday.
    private static final String MENTOR_REQUESTS = MENTOR_REQUESTS_MATCH + """
            RETURN p.id AS personId, p.firstName + ' ' + p.lastName AS personName,
                   s.id AS skillId, s.name AS skillName, w.createdAt AS wantedSince,
                   count { MATCH (c:Person)-[k:KNOWS]->(s)
                           WHERE c.isDeleted = false AND c.id <> p.id
                             AND k.level >= $minLevel } AS candidates
            ORDER BY wantedSince ASC, personName ASC, skillName ASC
            SKIP $skip LIMIT $limit
            """;

    private static final String COUNT_MENTOR_REQUESTS = MENTOR_REQUESTS_MATCH + "RETURN count(*) AS total";

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
    public List<DashboardResponse.SkillGapRow> skillGap(int threshold, long skip, int limit) {
        return List.copyOf(client.query(SKILL_GAP)
                .bindAll(Map.of("threshold", threshold, "skip", skip, "limit", limit))
                .fetchAs(DashboardResponse.SkillGapRow.class)
                .mappedBy((typeSystem, record) -> new DashboardResponse.SkillGapRow(
                        record.get("team").asString(),
                        record.get("skill").asString(),
                        sorted(record.get("projects").asList(Value::asString)),
                        record.get("knownBy").asLong()))
                .all());
    }

    public long countSkillGap(int threshold) {
        return client.query(COUNT_SKILL_GAP)
                .bind(threshold).to("threshold")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("total").asLong())
                .one()
                .orElse(0L);
    }

    /** @param minLevel the KNOWS level that makes someone mentor material (spec 4.3) */
    public List<DashboardResponse.MentorRequestRow> mentorRequests(int minLevel, long skip, int limit) {
        return List.copyOf(client.query(MENTOR_REQUESTS)
                .bindAll(Map.of("minLevel", minLevel, "skip", skip, "limit", limit))
                .fetchAs(DashboardResponse.MentorRequestRow.class)
                .mappedBy((typeSystem, record) -> new DashboardResponse.MentorRequestRow(
                        record.get("personId").asString(),
                        record.get("personName").asString(),
                        record.get("skillId").asString(),
                        record.get("skillName").asString(),
                        record.get("wantedSince").isNull()
                                ? null
                                : record.get("wantedSince").asZonedDateTime().toInstant(),
                        record.get("candidates").asLong()))
                .all());
    }

    public long countMentorRequests() {
        return client.query(COUNT_MENTOR_REQUESTS)
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("total").asLong())
                .one()
                .orElse(0L);
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
