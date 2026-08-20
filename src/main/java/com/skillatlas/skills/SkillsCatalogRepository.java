package com.skillatlas.skills;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import com.skillatlas.skills.dto.SkillCatalogResponse;
import com.skillatlas.skills.enums.SkillCategory;

/**
 * The catalog read: filters, ordering, paging and the three relationship counts in one query.
 *
 * <p>{@link Neo4jClient} rather than the derived repository, because a row is a projection (skill +
 * how many know it, want it, and which projects use it) — asking per row would be an N+1 over the
 * whole catalog.
 */
@Repository
public class SkillsCatalogRepository {

    // Both filters are bound $parameters, never concatenated.
    private static final String MATCH_SKILLS = """
            MATCH (s:Skill)
            WHERE ($search IS NULL OR toLower(s.name) CONTAINS $search)
              AND ($category IS NULL OR s.category = $category)
            """;

    // Deleted people still carry KNOWS edges, so the counts have to exclude them or the delete
    // impact overstates what is really there.
    private static final String PROJECTION = """
            WITH s,
                 count { MATCH (p:Person)-[:KNOWS]->(s) WHERE p.isDeleted = false } AS knownBy,
                 count { MATCH (p:Person)-[:WANTS_TO_LEARN]->(s) WHERE p.isDeleted = false }
                   AS wantedBy
            """;

    private static final String ORDER_BY_NAME = "ORDER BY toLower(s.name)\n";

    private static final String ORDER_BY_WANTED = "ORDER BY wantedBy DESC, knownBy ASC, toLower(s.name)\n";

    private static final String RETURN_ROWS = """
            SKIP $skip LIMIT $limit
            RETURN s.id AS id, s.name AS name, s.category AS category, s.color AS color,
                   knownBy, wantedBy,
                   [(pr:Project)-[:USES]->(s) | pr.name] AS usedBy
            """;

    private static final String FIND_BY_NAME = MATCH_SKILLS + PROJECTION + ORDER_BY_NAME + RETURN_ROWS;

    private static final String FIND_BY_WANTED = MATCH_SKILLS + PROJECTION + ORDER_BY_WANTED + RETURN_ROWS;

    private static final String COUNT_SKILLS = MATCH_SKILLS + "RETURN count(s) AS total";

    private final Neo4jClient client;

    public SkillsCatalogRepository(Neo4jClient client) {
        this.client = client;
    }

    /**
     * @param search   lowercased fragment matched against the skill name, or {@code null}
     * @param category exact category, or {@code null} for every category
     */
    public List<SkillCatalogResponse> find(String search, SkillCategory category, SkillSort sort,
            long skip, int limit) {
        Map<String, Object> params = params(search, category);
        params.put("skip", skip);
        params.put("limit", limit);
        return List.copyOf(client.query(sort == SkillSort.WANTED ? FIND_BY_WANTED : FIND_BY_NAME)
                .bindAll(params)
                .fetchAs(SkillCatalogResponse.class)
                .mappedBy((typeSystem, record) -> new SkillCatalogResponse(
                        record.get("id").asString(),
                        record.get("name").asString(),
                        SkillCategory.valueOf(record.get("category").asString()),
                        record.get("color").asString(null),
                        record.get("knownBy").asLong(),
                        record.get("wantedBy").asLong(),
                        record.get("usedBy").asList(Value::asString)))
                .all());
    }

    public long count(String search, SkillCategory category) {
        return client.query(COUNT_SKILLS)
                .bindAll(params(search, category))
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("total").asLong())
                .one()
                .orElse(0L);
    }

    // HashMap, not Map.of: an absent filter is bound as null.
    private static Map<String, Object> params(String search, SkillCategory category) {
        Map<String, Object> params = new HashMap<>();
        params.put("search", search);
        params.put("category", category == null ? null : category.name());
        return params;
    }
}
