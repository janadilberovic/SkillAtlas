package com.skillatlas.finder;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import com.skillatlas.finder.dto.ExpertResponse;

/**
 * The only place expert-finder Cypher lives.
 *
 * <p>Every value is bound as a {@code $parameter}, never concatenated into the query, so a skill
 * term like {@code React'}) DETACH DELETE (n) //} is treated as an ordinary (non-existent) skill
 * name: zero results, database untouched.
 *
 * <p>Uses {@link Neo4jClient} rather than a derived {@code Neo4jRepository}: the result is a
 * ranking projection (person + score + per-skill levels), not a {@code Person} entity, so the
 * row-to-DTO mapping is written out explicitly instead of being derived.
 */
@Repository
public class FinderRepository {

    // Shared head of both queries. count(DISTINCT s) = requiredCount is what makes this a real AND:
    // the person must know *every* requested skill, not any of them.
    private static final String MATCH_CANDIDATES = """
            MATCH (p:Person)-[r:KNOWS]->(s:Skill)
            WHERE p.isDeleted = false AND toLower(s.name) IN $skillNames
            WITH p, count(DISTINCT s) AS matched, sum(r.level) AS score,
                 collect({name: s.name, level: r.level}) AS matchedSkills
            WHERE matched = $requiredCount
              AND ($team IS NULL OR EXISTS {
                    MATCH (p)-[:MEMBER_OF]->(t:Team) WHERE toLower(t.name) = $team
                  })
            """;

    private static final String FIND_EXPERTS = MATCH_CANDIDATES + """
            RETURN p.id AS id, p.email AS email, p.firstName AS firstName,
                   p.lastName AS lastName, p.position AS position,
                   score, matchedSkills
            ORDER BY score DESC, lastName ASC
            SKIP $skip LIMIT $limit
            """;

    private static final String COUNT_EXPERTS = MATCH_CANDIDATES + "RETURN count(p) AS total";

    private final Neo4jClient client;

    public FinderRepository(Neo4jClient client) {
        this.client = client;
    }

    /**
     * @param skillNames lowercased skill names the person must know — all of them
     * @param team       lowercased team name, or {@code null} for no team filter
     */
    public List<ExpertResponse> findExperts(Collection<String> skillNames, String team, long skip, int limit) {
        Map<String, Object> params = params(skillNames, team);
        params.put("skip", skip);
        params.put("limit", limit);
        return List.copyOf(client.query(FIND_EXPERTS)
                .bindAll(params)
                .fetchAs(ExpertResponse.class)
                .mappedBy((typeSystem, record) -> new ExpertResponse(
                        record.get("id").asString(),
                        record.get("email").asString(),
                        record.get("firstName").asString(),
                        record.get("lastName").asString(),
                        record.get("position").asString(null),
                        record.get("score").asInt(),
                        record.get("matchedSkills").asList(v -> new ExpertResponse.MatchedSkill(
                                v.get("name").asString(), v.get("level").asInt()))
                                .stream()
                                // collect() has no guaranteed order; sort so responses are stable.
                                .sorted(Comparator.comparingInt(ExpertResponse.MatchedSkill::level).reversed()
                                        .thenComparing(ExpertResponse.MatchedSkill::name))
                                .toList()))
                .all());
    }

    public long countExperts(Collection<String> skillNames, String team) {
        return client.query(COUNT_EXPERTS)
                .bindAll(params(skillNames, team))
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("total").asLong())
                .one()
                .orElse(0L);
    }

    // HashMap, not Map.of: `team` is null when no team filter is requested.
    private Map<String, Object> params(Collection<String> skillNames, String team) {
        Map<String, Object> params = new HashMap<>();
        params.put("skillNames", skillNames);
        params.put("requiredCount", skillNames.size());
        params.put("team", team);
        return params;
    }
}
