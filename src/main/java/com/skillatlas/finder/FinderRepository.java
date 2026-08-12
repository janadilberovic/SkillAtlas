package com.skillatlas.finder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import com.skillatlas.finder.dto.ExpertResponse;
import com.skillatlas.finder.dto.SkillCoverageResponse;

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

    // Shared head of both queries. Two filters do the real work:
    //   r.level >= $minLevels[...]  drops KNOWS edges below the threshold the caller asked for,
    //   count(DISTINCT s) = requiredCount  makes it an AND — the person must clear the bar on
    //   *every* requested skill, not any of them.
    private static final String MATCH_CANDIDATES = """
            MATCH (p:Person)-[r:KNOWS]->(s:Skill)
            WHERE p.isDeleted = false
              AND toLower(s.name) IN $skillNames
              AND r.level >= $minLevels[toLower(s.name)]
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
                   [(p)-[:MEMBER_OF]->(t:Team) | t.name] AS teams,
                   score, matchedSkills
            ORDER BY score DESC, lastName ASC
            SKIP $skip LIMIT $limit
            """;

    private static final String COUNT_EXPERTS = MATCH_CANDIDATES + "RETURN count(p) AS total";

    // Bus factor input, one row per requested skill. OPTIONAL MATCH so a skill nobody knows still
    // comes back (knownBy 0) instead of vanishing; collect() drops the nulls the CASE produces for
    // people below the expert level.
    private static final String SKILL_COVERAGE = """
            UNWIND $skillNames AS name
            OPTIONAL MATCH (p:Person)-[r:KNOWS]->(s:Skill)
            WHERE p.isDeleted = false AND toLower(s.name) = name
            WITH name,
                 count(p) AS knownBy,
                 head(collect(s.name)) AS displayName,
                 collect(CASE WHEN r.level >= $expertLevel
                              THEN p.firstName + ' ' + p.lastName END) AS experts
            RETURN coalesce(displayName, name) AS skill, knownBy, experts
            """;

    private final Neo4jClient client;

    public FinderRepository(Neo4jClient client) {
        this.client = client;
    }

    /**
     * @param terms one lowercased skill name + minimum level per term — the person must clear all
     * @param team  lowercased team name, or {@code null} for no team filter
     */
    public List<ExpertResponse> findExperts(Collection<SkillTerm> terms, String team, long skip, int limit) {
        Map<String, Object> params = params(terms, team);
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
                        sorted(record.get("teams").asList(v -> v.asString())),
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

    public long countExperts(Collection<SkillTerm> terms, String team) {
        return client.query(COUNT_EXPERTS)
                .bindAll(params(terms, team))
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("total").asLong())
                .one()
                .orElse(0L);
    }

    /**
     * @param skillNames lowercased skill names
     * @param expertLevel lowest KNOWS level that makes someone a go-to person for a skill
     */
    public List<SkillCoverageResponse> skillCoverage(Collection<String> skillNames, int expertLevel) {
        return List.copyOf(client.query(SKILL_COVERAGE)
                .bindAll(Map.of("skillNames", skillNames, "expertLevel", expertLevel))
                .fetchAs(SkillCoverageResponse.class)
                .mappedBy((typeSystem, record) -> new SkillCoverageResponse(
                        record.get("skill").asString(),
                        record.get("knownBy").asLong(),
                        sorted(record.get("experts").asList(v -> v.asString()))))
                .all());
    }

    // HashMap, not Map.of: `team` is null when no team filter is requested.
    private Map<String, Object> params(Collection<SkillTerm> terms, String team) {
        Map<String, Object> minLevels = new LinkedHashMap<>();
        terms.forEach(t -> minLevels.put(t.name(), t.minLevel()));

        Map<String, Object> params = new HashMap<>();
        params.put("skillNames", List.copyOf(minLevels.keySet()));
        params.put("minLevels", minLevels);
        params.put("requiredCount", minLevels.size());
        params.put("team", team);
        return params;
    }

    private static List<String> sorted(List<String> values) {
        List<String> copy = new ArrayList<>(values);
        copy.sort(Comparator.naturalOrder());
        return List.copyOf(copy);
    }
}
