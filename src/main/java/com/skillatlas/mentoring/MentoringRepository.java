package com.skillatlas.mentoring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.neo4j.driver.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import com.skillatlas.graph.dto.GraphEdge;
import com.skillatlas.graph.dto.GraphNode;
import com.skillatlas.graph.enums.GraphNodeKind;
import com.skillatlas.mentoring.dto.LearningPathResponse;
import com.skillatlas.mentoring.dto.MentorCandidate;
import com.skillatlas.mentoring.dto.SkillRef;

/**
 * The only place mentoring Cypher lives. Every value is bound as a {@code $parameter}, so a skill
 * name carrying Cypher resolves to no skill at all and the database is untouched.
 *
 * <p>{@link Neo4jClient} rather than a derived repository, for the same reason the finder uses it:
 * these are ranking and path projections, not any one entity.
 */
@Repository
public class MentoringRepository {

    private static final String FIND_SKILL = """
            MATCH (s:Skill) WHERE toLower(s.name) = $name
            RETURN s.id AS id, s.name AS name
            """;

    // 4.3, all four rules in one pass: knows the skill at least at $minLevel, is not the mentee,
    // is not soft-deleted, and does not already mentor them on this skill. Load is a COUNT
    // subquery rather than a second query, so ranking stays one round trip.
    private static final String MENTOR_CANDIDATES = """
            MATCH (mentee:Person {id: $menteeId}) WHERE mentee.isDeleted = false
            MATCH (c:Person)-[r:KNOWS]->(s:Skill {id: $skillId})
            WHERE c.isDeleted = false
              AND c.id <> mentee.id
              AND r.level >= $minLevel
              AND NOT EXISTS {
                    MATCH (c)-[m:MENTORS {skillId: s.id}]->(mentee) }
            RETURN c.id AS id, c.email AS email, c.firstName AS firstName, c.lastName AS lastName,
                   c.position AS position,
                   [(c)-[:MEMBER_OF]->(t:Team) | t.name] AS teams,
                   r.level AS level,
                   count { MATCH (c)-[:MENTORS]->(m:Person) WHERE m.isDeleted = false }
                     AS activeMentorships
            ORDER BY level DESC, activeMentorships ASC, lastName ASC
            LIMIT $limit
            """;

    // 4.4 lists the four relationship types a path may walk; WANTS_TO_LEARN is not among them, and
    // rightly so - wanting to learn something is not a step towards knowing it.
    // Undirected on purpose: KNOWS runs person->skill but USES runs project->skill, so a directed
    // pattern could never reach the skill through a project.
    // The all() predicate is the soft-delete filter - a path may not be routed through someone the
    // company has deleted.
    private static final String LEARNING_PATH = """
            MATCH (p:Person {id: $personId}) WHERE p.isDeleted = false
            MATCH (s:Skill {id: $skillId})
            MATCH path = shortestPath((p)-[:KNOWS|WORKED_ON|USES|MEMBER_OF*1..%d]-(s))
            WHERE all(n IN nodes(path) WHERE NOT n:Person OR n.isDeleted = false)
            RETURN [n IN nodes(path) | {
                     id: n.id,
                     kind: CASE WHEN n:Person THEN 'PERSON' WHEN n:Skill THEN 'SKILL'
                                WHEN n:Project THEN 'PROJECT' ELSE 'TEAM' END,
                     label: CASE WHEN n:Person THEN n.firstName + ' ' + n.lastName ELSE n.name END,
                     meta: CASE WHEN n:Person THEN n.position
                                WHEN n:Skill THEN n.category
                                WHEN n:Project THEN
                                     CASE WHEN n.active THEN 'Active' ELSE 'Finished' END
                                ELSE 'Team' END }] AS nodes,
                   [r IN relationships(path) |
                     {type: type(r), source: startNode(r).id, target: endNode(r).id}] AS edges,
                   head([(p)-[k:KNOWS]->(s) | k.level]) AS ownLevel
            """;

    // The mentors among the people on the path, in one query rather than one per step. Which of
    // them is *nearest* is decided in Java, where the walk order already is.
    private static final String MENTORS_AMONG = """
            MATCH (c:Person)-[k:KNOWS]->(:Skill {id: $skillId})
            WHERE c.id IN $personIds AND c.isDeleted = false AND k.level >= $minLevel
            RETURN c.id AS id, c.firstName + ' ' + c.lastName AS name, k.level AS level
            """;

    private final Neo4jClient client;

    public MentoringRepository(Neo4jClient client) {
        this.client = client;
    }

    /** @param name lowercased skill name */
    public Optional<SkillRef> findSkillByName(String name) {
        return client.query(FIND_SKILL)
                .bind(name).to("name")
                .fetchAs(SkillRef.class)
                .mappedBy((typeSystem, record) -> new SkillRef(
                        record.get("id").asString(), record.get("name").asString()))
                .one();
    }

    public List<MentorCandidate> mentorCandidates(String menteeId, String skillId, int minLevel, int limit) {
        return List.copyOf(client.query(MENTOR_CANDIDATES)
                .bindAll(Map.of("menteeId", menteeId, "skillId", skillId,
                        "minLevel", minLevel, "limit", limit))
                .fetchAs(MentorCandidate.class)
                .mappedBy((typeSystem, record) -> new MentorCandidate(
                        record.get("id").asString(),
                        record.get("email").asString(),
                        record.get("firstName").asString(),
                        record.get("lastName").asString(),
                        record.get("position").asString(null),
                        sorted(record.get("teams").asList(Value::asString)),
                        record.get("level").asInt(),
                        record.get("activeMentorships").asLong()))
                .all());
    }

    /** The walk itself; the nearest mentor on it is filled in from {@link #mentorsAmong}. */
    public Optional<LearningPathResponse> learningPath(String personId, SkillRef skill, int maxHops) {
        return client.query(LEARNING_PATH.formatted(maxHops))
                .bindAll(Map.of("personId", personId, "skillId", skill.id()))
                .fetchAs(LearningPathResponse.class)
                .mappedBy((typeSystem, record) -> {
                    List<GraphNode> nodes = record.get("nodes").asList(MentoringRepository::node);
                    List<GraphEdge> edges = record.get("edges").asList(v -> new GraphEdge(
                            v.get("source").asString(),
                            v.get("target").asString(),
                            v.get("type").asString()));
                    Integer ownLevel = record.get("ownLevel").isNull()
                            ? null
                            : record.get("ownLevel").asInt();
                    return new LearningPathResponse(personId, skill, true, edges.size(),
                            ownLevel, nodes, edges, null);
                })
                .one();
    }

    /** Who among {@code personIds} knows {@code skillId} well enough to mentor, keyed by person id. */
    public Map<String, LearningPathResponse.NearestMentor> mentorsAmong(
            Collection<String> personIds, String skillId, int minLevel) {
        Map<String, LearningPathResponse.NearestMentor> mentors = new LinkedHashMap<>();
        client.query(MENTORS_AMONG)
                .bindAll(Map.of("personIds", List.copyOf(personIds), "skillId", skillId,
                        "minLevel", minLevel))
                .fetchAs(LearningPathResponse.NearestMentor.class)
                .mappedBy((typeSystem, record) -> new LearningPathResponse.NearestMentor(
                        record.get("id").asString(),
                        record.get("name").asString(),
                        record.get("level").asInt()))
                .all()
                .forEach(m -> mentors.put(m.id(), m));
        return mentors;
    }

    private static GraphNode node(Value v) {
        return new GraphNode(
                v.get("id").asString(),
                GraphNodeKind.valueOf(v.get("kind").asString()),
                v.get("label").asString(),
                v.get("meta").asString(null));
    }

    private static List<String> sorted(List<String> values) {
        List<String> copy = new ArrayList<>(values);
        copy.sort(Comparator.naturalOrder());
        return List.copyOf(copy);
    }
}
