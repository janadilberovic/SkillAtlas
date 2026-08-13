package com.skillatlas.graph;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.neo4j.driver.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import com.skillatlas.graph.dto.GraphEdge;
import com.skillatlas.graph.dto.GraphNode;
import com.skillatlas.graph.dto.GraphResponse;
import com.skillatlas.graph.enums.GraphNodeKind;

/**
 * The explorer subgraph, in two fixed queries: pick a set of seed people, then collect the edges
 * hanging off them. Two round trips regardless of result size — the seed is never re-queried per
 * person.
 *
 * <p>People are always the seed even when {@code PERSON} is filtered out of the view, because every
 * relationship in this model except {@code USES} starts at a Person. Dropping the kind drops the
 * person nodes and their edges from the answer; it does not change what the traversal walks. That
 * is also what keeps the {@code team} filter meaningful for every combination of types.
 *
 * <p>{@link Neo4jClient} rather than a derived repository: the result is a projection across six
 * relationship types, not any one entity.
 */
@Repository
public class GraphRepository {

    // Every read of Person filters soft-delete, here and again on the far side of MENTORS.
    // $team is bound, never concatenated: React'}) DETACH DELETE (n) // is a team name nobody has.
    private static final String SEED = """
            MATCH (p:Person)
            WHERE p.isDeleted = false
              AND ($team IS NULL OR EXISTS {
                    MATCH (p)-[:MEMBER_OF]->(t:Team) WHERE toLower(t.name) = $team })
            RETURN p.id AS id
            ORDER BY p.lastName, p.firstName
            LIMIT $seedLimit
            """;

    // Rooted view behind the profile's "in the graph" jump. hops = 1 is the root alone; hops = 2
    // adds the colleagues it shares a skill, project or team with, and its mentors and mentees.
    // Neo4j will not take a parameter for a variable-length bound, so depth is two explicit
    // patterns rather than [*1..$hops] — which also keeps the expansion from running away.
    private static final String ROOTED_SEED = """
            MATCH (root:Person {id: $rootId})
            WHERE root.isDeleted = false
            WITH root, CASE WHEN $hops >= 2 THEN
                   [(root)-[:KNOWS|WANTS_TO_LEARN|WORKED_ON|MEMBER_OF]->(x)
                     <-[:KNOWS|WANTS_TO_LEARN|WORKED_ON|MEMBER_OF]-(c:Person)
                     WHERE c.isDeleted = false | c.id]
                 + [(root)-[:MENTORS]-(c:Person) WHERE c.isDeleted = false | c.id]
                 ELSE [] END AS raw
            UNWIND (CASE WHEN raw = [] THEN [null] ELSE raw END) AS candidate
            WITH root, collect(DISTINCT candidate) AS colleagues
            UNWIND [root.id] + [c IN colleagues
                                WHERE c IS NOT NULL AND c <> root.id][0..$seedLimit] AS id
            RETURN id
            """;

    // One pattern comprehension per relationship type, each gated on both of its endpoint kinds
    // being requested — an edge to a node the caller filtered out is not a drawable edge.
    // The cap is applied in the database (rels[0..$limit]); size(rels) still reports the true
    // total, so "showing 150 of 1204" is not a guess.
    private static final String EDGES = """
            MATCH (p:Person)
            WHERE p.id IN $ids
            WITH p, {id: p.id, kind: 'PERSON',
                     label: p.firstName + ' ' + p.lastName, meta: p.position} AS self
            WITH p, self,
                   CASE WHEN $knows THEN
                     [(p)-[:KNOWS]->(s:Skill) |
                        {type: 'KNOWS', source: self,
                         target: {id: s.id, kind: 'SKILL', label: s.name, meta: s.category}}]
                   ELSE [] END
                 + CASE WHEN $knows THEN
                     [(p)-[:WANTS_TO_LEARN]->(s:Skill) |
                        {type: 'WANTS_TO_LEARN', source: self,
                         target: {id: s.id, kind: 'SKILL', label: s.name, meta: s.category}}]
                   ELSE [] END
                 + CASE WHEN $workedOn THEN
                     [(p)-[:WORKED_ON]->(pr:Project) |
                        {type: 'WORKED_ON', source: self,
                         target: {id: pr.id, kind: 'PROJECT', label: pr.name,
                                  meta: CASE WHEN pr.active THEN 'Active' ELSE 'Finished' END}}]
                   ELSE [] END
                 + CASE WHEN $memberOf THEN
                     [(p)-[:MEMBER_OF]->(t:Team) |
                        {type: 'MEMBER_OF', source: self,
                         target: {id: t.id, kind: 'TEAM', label: t.name, meta: 'Team'}}]
                   ELSE [] END
                 + CASE WHEN $mentors THEN
                     [(p)-[:MENTORS]->(o:Person) WHERE o.isDeleted = false |
                        {type: 'MENTORS', source: self,
                         target: {id: o.id, kind: 'PERSON',
                                  label: o.firstName + ' ' + o.lastName, meta: o.position}}]
                   ELSE [] END
                 + CASE WHEN $uses THEN
                     [(p)-[:WORKED_ON]->(pr:Project)-[:USES]->(s:Skill) |
                        {type: 'USES',
                         source: {id: pr.id, kind: 'PROJECT', label: pr.name,
                                  meta: CASE WHEN pr.active THEN 'Active' ELSE 'Finished' END},
                         target: {id: s.id, kind: 'SKILL', label: s.name, meta: s.category}}]
                   ELSE [] END
                 AS rels
            WITH collect(self) AS selves, collect(rels) AS lists
            WITH selves, reduce(all = [], l IN lists | all + l) AS rels
            RETURN selves, size(rels) AS total, rels[0..$limit] AS window
            """;

    private static final String TOTALS = """
            RETURN count { MATCH (p:Person) WHERE p.isDeleted = false } AS PERSON,
                   count { MATCH (s:Skill) } AS SKILL,
                   count { MATCH (pr:Project) } AS PROJECT,
                   count { MATCH (t:Team) } AS TEAM
            """;

    private final Neo4jClient client;

    public GraphRepository(Neo4jClient client) {
        this.client = client;
    }

    /** Ids of the people the subgraph is grown from; empty when the root is missing or deleted. */
    public List<String> seedPeople(String rootId, int hops, String team, int seedLimit) {
        String query = rootId == null ? SEED : ROOTED_SEED;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("seedLimit", seedLimit);
        if (rootId == null) {
            // Neo4jClient's Map.of route rejects a null, and the query still needs the key bound.
            params.put("team", team);
        } else {
            params.put("rootId", rootId);
            params.put("hops", hops);
        }
        return List.copyOf(client.query(query)
                .bindAll(params)
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("id").asString())
                .all());
    }

    /** The edges hanging off {@code personIds}, capped at {@code limit} relationships. */
    public GraphResponse subgraph(List<String> personIds, Set<GraphNodeKind> kinds, int limit) {
        boolean people = kinds.contains(GraphNodeKind.PERSON);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ids", personIds);
        params.put("limit", limit);
        params.put("knows", people && kinds.contains(GraphNodeKind.SKILL));
        params.put("workedOn", people && kinds.contains(GraphNodeKind.PROJECT));
        params.put("memberOf", people && kinds.contains(GraphNodeKind.TEAM));
        params.put("mentors", people);
        params.put("uses", kinds.contains(GraphNodeKind.PROJECT) && kinds.contains(GraphNodeKind.SKILL));

        return client.query(EDGES)
                .bindAll(params)
                .fetchAs(GraphResponse.class)
                .mappedBy((typeSystem, record) -> {
                    Map<String, GraphNode> nodes = new LinkedHashMap<>();
                    // Seeded with the people themselves, who would otherwise vanish when they have
                    // no edges — an isolated person is a finding, not a reason to hide them.
                    if (people) {
                        record.get("selves").asList(v -> v).forEach(v -> {
                            GraphNode self = node(v);
                            nodes.put(self.id(), self);
                        });
                    }
                    Set<GraphEdge> edges = new LinkedHashSet<>();
                    record.get("window").asList(v -> v).forEach(v -> {
                        GraphNode source = node(v.get("source"));
                        GraphNode target = node(v.get("target"));
                        nodes.putIfAbsent(source.id(), source);
                        nodes.putIfAbsent(target.id(), target);
                        edges.add(new GraphEdge(source.id(), target.id(), v.get("type").asString()));
                    });
                    int total = record.get("total").asInt();
                    return new GraphResponse(List.copyOf(nodes.values()), List.copyOf(edges),
                            total, Map.of(), total > record.get("window").size());
                })
                .one()
                .orElse(new GraphResponse(List.of(), List.of(), 0, Map.of(), false));
    }

    /** Company-wide node counts for the legend — deliberately unfiltered. */
    public Map<GraphNodeKind, Long> totals() {
        Map<String, Object> row = client.query(TOTALS).fetch().one().orElse(Map.of());
        Map<GraphNodeKind, Long> totals = new EnumMap<>(GraphNodeKind.class);
        for (GraphNodeKind kind : GraphNodeKind.values()) {
            Object value = row.get(kind.name());
            totals.put(kind, value instanceof Number number ? number.longValue() : 0L);
        }
        return totals;
    }

    private static GraphNode node(Value v) {
        return new GraphNode(
                v.get("id").asString(),
                GraphNodeKind.valueOf(v.get("kind").asString()),
                v.get("label").asString(),
                v.get("meta").asString(null));
    }
}
