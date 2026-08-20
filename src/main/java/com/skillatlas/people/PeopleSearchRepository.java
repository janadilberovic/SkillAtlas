package com.skillatlas.people;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import com.skillatlas.people.dto.PersonResponse;
import com.skillatlas.people.dto.PersonResponse.TopSkill;
import com.skillatlas.people.enums.Role;

/**
 * The people list read: soft-delete filter, the three optional filters, ordering and paging in one
 * query.
 *
 * <p>{@link Neo4jClient} rather than a derived repository, because a row is the list projection
 * (person + team names + top skills), not a {@code Person} entity — the teams and skills come back
 * in the same round trip instead of one query per row.
 */
@Repository
public class PeopleSearchRepository {

    // Every filter is a bound $parameter, never concatenated, so React'}) DETACH DELETE (n) // is
    // just a search string that matches nobody.
    private static final String MATCH_PEOPLE = """
            MATCH (p:Person)
            WHERE p.isDeleted = false
              AND ($search IS NULL OR
                   toLower(p.firstName + ' ' + p.lastName + ' ' + p.email) CONTAINS $search)
              AND ($team IS NULL OR EXISTS {
                    MATCH (p)-[:MEMBER_OF]->(t:Team) WHERE toLower(t.name) = $team
                  })
              AND ($skill IS NULL OR EXISTS {
                    MATCH (p)-[:KNOWS]->(s:Skill) WHERE toLower(s.name) = $skill
                  })
            """;

    // Most recently added first, then alphabetical. The (createdAt IS NULL) key comes first because
    // a plain DESC sorts nulls to the *front* in Cypher, which would put everyone who predates the
    // field above the newest hire; false sorts before true, so they fall to the back instead.
    private static final String FIND_PEOPLE = MATCH_PEOPLE + """
            WITH p
            ORDER BY (p.createdAt IS NULL), p.createdAt DESC,
                     toLower(p.lastName), toLower(p.firstName)
            SKIP $skip LIMIT $limit
            RETURN p.id AS id, p.email AS email, p.firstName AS firstName, p.lastName AS lastName,
                   p.position AS position, p.role AS role, p.active AS active,
                   [(p)-[:MEMBER_OF]->(t:Team) | t.name] AS teams,
                   [(p)-[r:KNOWS]->(s:Skill) | {skillId: s.id, name: s.name, level: r.level}]
                     AS skills
            """;

    private static final String COUNT_PEOPLE = MATCH_PEOPLE + "RETURN count(p) AS total";

    private final Neo4jClient client;

    public PeopleSearchRepository(Neo4jClient client) {
        this.client = client;
    }

    /**
     * @param search lowercased fragment matched against "first last email", or {@code null}
     * @param team   lowercased team name, or {@code null} for no team filter
     * @param skill  lowercased skill name, or {@code null} for no skill filter
     */
    public List<PersonResponse> find(String search, String team, String skill, long skip, int limit) {
        Map<String, Object> params = params(search, team, skill);
        params.put("skip", skip);
        params.put("limit", limit);
        return List.copyOf(client.query(FIND_PEOPLE)
                .bindAll(params)
                .fetchAs(PersonResponse.class)
                .mappedBy((typeSystem, record) -> PersonResponse.of(
                        record.get("id").asString(),
                        record.get("email").asString(),
                        record.get("firstName").asString(),
                        record.get("lastName").asString(),
                        record.get("position").asString(null),
                        Role.valueOf(record.get("role").asString()),
                        record.get("active").asBoolean(true),
                        record.get("teams").asList(Value::asString),
                        record.get("skills").asList(v -> new TopSkill(
                                v.get("skillId").asString(),
                                v.get("name").asString(),
                                v.get("level").asInt()))))
                .all());
    }

    public long count(String search, String team, String skill) {
        return client.query(COUNT_PEOPLE)
                .bindAll(params(search, team, skill))
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("total").asLong())
                .one()
                .orElse(0L);
    }

    // HashMap, not Map.of: an absent filter is bound as null.
    private static Map<String, Object> params(String search, String team, String skill) {
        Map<String, Object> params = new HashMap<>();
        params.put("search", search);
        params.put("team", team);
        params.put("skill", skill);
        return params;
    }
}
