package com.skillatlas.people;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.neo4j.driver.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import com.skillatlas.people.dto.PersonProfileResponse;
import com.skillatlas.people.dto.PersonProfileResponse.GraphEdge;
import com.skillatlas.people.dto.PersonProfileResponse.GraphNode;
import com.skillatlas.people.dto.PersonProfileResponse.KnownSkill;
import com.skillatlas.people.dto.PersonProfileResponse.Mentoring;
import com.skillatlas.people.dto.PersonProfileResponse.Mentorship;
import com.skillatlas.people.dto.PersonProfileResponse.Neighbourhood;
import com.skillatlas.people.dto.PersonProfileResponse.ProjectMembership;
import com.skillatlas.people.dto.PersonProfileResponse.WishedSkill;
import com.skillatlas.people.enums.Role;

/**
 * The only place the rich-profile Cypher lives (E4.2).
 *
 * <p>Uses {@link Neo4jClient} rather than a derived {@code Neo4jRepository}: the result is an
 * aggregate projection across five relationship types, not a {@code Person} entity, so the
 * row-to-DTO mapping is written out explicitly.
 *
 * <p>The profile is one query with {@code COLLECT {}} subqueries — one per branch. A chain of
 * {@code OPTIONAL MATCH} would multiply rows instead (5 skills × 3 projects × 2 mentees = 30 rows
 * for one person), and every aggregate downstream would then have to undo that. {@code COLLECT {}}
 * needs Neo4j ≥ 5.6; the pinned image is 5.26.
 *
 * <p>The id arrives as a path variable and is bound as {@code $id} — never concatenated — so
 * {@code React'}) DETACH DELETE (n) //} is just an id that matches no person.
 */
@Repository
public class PeopleProfileRepository {

    // Soft delete is filtered on the person and again on the far side of both MENTORS directions:
    // a deleted colleague must not surface through someone else's profile.
    private static final String PROFILE = """
            MATCH (p:Person {id: $id})
            WHERE p.isDeleted = false
            RETURN p.id AS id, p.email AS email, p.firstName AS firstName, p.lastName AS lastName,
                   p.position AS position, p.role AS role, p.active AS active,
                   [(p)-[:MEMBER_OF]->(t:Team) | t.name] AS teams,
                   COLLECT {
                     MATCH (p)-[r:KNOWS]->(s:Skill)
                     RETURN {skillId: s.id, name: s.name, category: s.category, color: s.color,
                             level: r.level, since: r.since}
                   } AS skills,
                   COLLECT {
                     MATCH (p)-[:WANTS_TO_LEARN]->(s:Skill)
                     RETURN {skillId: s.id, name: s.name, category: s.category, color: s.color}
                   } AS wishes,
                   COLLECT {
                     MATCH (p)-[w:WORKED_ON]->(pr:Project)
                     RETURN {projectId: pr.id, name: pr.name, role: w.role, from: w.from, to: w.to,
                             active: pr.active, uses: [(pr)-[:USES]->(u:Skill) | u.name]}
                   } AS projects,
                   COLLECT {
                     MATCH (p)-[m:MENTORS]->(other:Person)
                     WHERE other.isDeleted = false
                     OPTIONAL MATCH (s:Skill {id: m.skillId})
                     RETURN {personId: other.id, name: other.firstName + ' ' + other.lastName,
                             skill: s.name, since: m.since}
                   } AS mentees,
                   COLLECT {
                     MATCH (p)<-[m:MENTORS]-(other:Person)
                     WHERE other.isDeleted = false
                     OPTIONAL MATCH (s:Skill {id: m.skillId})
                     RETURN {personId: other.id, name: other.firstName + ' ' + other.lastName,
                             skill: s.name, since: m.since}
                   } AS mentors
            """;

    // One hop out of the person, plus the single second hop worth drawing (what their projects
    // USE). `rels[0..$limit]` applies the cap in the database — the browser never gets an
    // unbounded subgraph — and `size(rels)` still reports the true degree so the client can say
    // the picture is partial.
    private static final String NEIGHBOURHOOD = """
            MATCH (p:Person {id: $id})
            WHERE p.isDeleted = false
            WITH p, {id: p.id, kind: 'PERSON',
                     label: p.firstName + ' ' + p.lastName, meta: p.position} AS root
            WITH p, root,
                   [(p)-[:KNOWS]->(s:Skill) |
                      {type: 'KNOWS', source: root,
                       target: {id: s.id, kind: 'SKILL', label: s.name, meta: s.category}}]
                 + [(p)-[:WANTS_TO_LEARN]->(s:Skill) |
                      {type: 'WANTS_TO_LEARN', source: root,
                       target: {id: s.id, kind: 'SKILL', label: s.name, meta: s.category}}]
                 + [(p)-[:WORKED_ON]->(pr:Project) |
                      {type: 'WORKED_ON', source: root,
                       target: {id: pr.id, kind: 'PROJECT', label: pr.name,
                                meta: CASE WHEN pr.active THEN 'Active' ELSE 'Finished' END}}]
                 + [(p)-[:MEMBER_OF]->(t:Team) |
                      {type: 'MEMBER_OF', source: root,
                       target: {id: t.id, kind: 'TEAM', label: t.name, meta: 'Team'}}]
                 + [(p)-[:MENTORS]->(o:Person) WHERE o.isDeleted = false |
                      {type: 'MENTORS', source: root,
                       target: {id: o.id, kind: 'PERSON',
                                label: o.firstName + ' ' + o.lastName, meta: o.position}}]
                 + [(p)<-[:MENTORS]-(o:Person) WHERE o.isDeleted = false |
                      {type: 'MENTORS',
                       source: {id: o.id, kind: 'PERSON',
                                label: o.firstName + ' ' + o.lastName, meta: o.position},
                       target: root}]
                 + [(p)-[:WORKED_ON]->(pr:Project)-[:USES]->(s:Skill) |
                      {type: 'USES',
                       source: {id: pr.id, kind: 'PROJECT', label: pr.name,
                                meta: CASE WHEN pr.active THEN 'Active' ELSE 'Finished' END},
                       target: {id: s.id, kind: 'SKILL', label: s.name, meta: s.category}}]
                 AS rels
            RETURN root, size(rels) AS total, rels[0..$limit] AS window
            """;

    private final Neo4jClient client;

    public PeopleProfileRepository(Neo4jClient client) {
        this.client = client;
    }

    /** Empty when the person does not exist or is soft-deleted. Neighbourhood is filled separately. */
    public Optional<PersonProfileResponse> findProfile(String id) {
        return client.query(PROFILE)
                .bind(id).to("id")
                .fetchAs(PersonProfileResponse.class)
                .mappedBy((typeSystem, record) -> new PersonProfileResponse(
                        record.get("id").asString(),
                        record.get("email").asString(),
                        record.get("firstName").asString(),
                        record.get("lastName").asString(),
                        record.get("position").asString(null),
                        Role.valueOf(record.get("role").asString()),
                        record.get("active").asBoolean(),
                        sorted(record.get("teams").asList(Value::asString)),
                        record.get("skills").asList(PeopleProfileRepository::knownSkill).stream()
                                .sorted(Comparator.comparingInt(KnownSkill::level).reversed()
                                        .thenComparing(KnownSkill::name))
                                .toList(),
                        record.get("wishes").asList(PeopleProfileRepository::wishedSkill).stream()
                                .sorted(Comparator.comparing(WishedSkill::name))
                                .toList(),
                        record.get("projects").asList(PeopleProfileRepository::project).stream()
                                // Ongoing work (no end date) first, then most recently finished.
                                .sorted(Comparator.comparing(ProjectMembership::to,
                                        Comparator.nullsFirst(Comparator.reverseOrder()))
                                        .thenComparing(ProjectMembership::name))
                                .toList(),
                        new Mentoring(
                                mentorships(record.get("mentees")),
                                mentorships(record.get("mentors"))),
                        // Placeholder: the caller composes the neighbourhood in a second query.
                        new Neighbourhood(List.of(), List.of(), false)))
                .one();
    }

    /**
     * The subgraph around a person, capped at {@code limit} relationships.
     *
     * <p>Empty when the person is missing or soft-deleted — the same filter as
     * {@link #findProfile(String)}, so a profile is never served with someone else's surroundings.
     */
    public Neighbourhood neighbourhood(String id, int limit) {
        return client.query(NEIGHBOURHOOD)
                .bindAll(Map.of("id", id, "limit", limit))
                .fetchAs(Neighbourhood.class)
                .mappedBy((typeSystem, record) -> {
                    List<Value> window = record.get("window").asList(v -> v);
                    // Nodes are whatever the surviving edges touch; the person is always present,
                    // even when they have no relationships at all.
                    Map<String, GraphNode> nodes = new LinkedHashMap<>();
                    GraphNode root = node(record.get("root"));
                    nodes.put(root.id(), root);
                    Set<GraphEdge> edges = new LinkedHashSet<>();
                    // The slice above is applied in pattern-match order, so sort here to keep the
                    // response stable for a given graph.
                    window.stream()
                            .sorted(Comparator
                                    .comparing((Value v) -> v.get("type").asString())
                                    .thenComparing(v -> v.get("target").get("label").asString()))
                            .forEach(v -> {
                                GraphNode source = node(v.get("source"));
                                GraphNode target = node(v.get("target"));
                                nodes.putIfAbsent(source.id(), source);
                                nodes.putIfAbsent(target.id(), target);
                                edges.add(new GraphEdge(source.id(), target.id(),
                                        v.get("type").asString()));
                            });
                    boolean truncated = record.get("total").asInt() > window.size();
                    return new Neighbourhood(List.copyOf(nodes.values()), List.copyOf(edges),
                            truncated);
                })
                .one()
                .orElse(new Neighbourhood(List.of(), List.of(), false));
    }

    private static KnownSkill knownSkill(Value v) {
        return new KnownSkill(
                v.get("skillId").asString(),
                v.get("name").asString(),
                v.get("category").asString(null),
                v.get("color").asString(null),
                v.get("level").asInt(),
                date(v.get("since")));
    }

    private static WishedSkill wishedSkill(Value v) {
        return new WishedSkill(
                v.get("skillId").asString(),
                v.get("name").asString(),
                v.get("category").asString(null),
                v.get("color").asString(null));
    }

    private static ProjectMembership project(Value v) {
        return new ProjectMembership(
                v.get("projectId").asString(),
                v.get("name").asString(),
                v.get("role").asString(null),
                date(v.get("from")),
                date(v.get("to")),
                v.get("active").asBoolean(false),
                sorted(v.get("uses").asList(Value::asString)));
    }

    private static List<Mentorship> mentorships(Value value) {
        return value.asList(v -> new Mentorship(
                v.get("personId").asString(),
                v.get("name").asString(),
                v.get("skill").asString(null),
                date(v.get("since"))))
                .stream()
                .sorted(Comparator.comparing(Mentorship::name))
                .toList();
    }

    private static GraphNode node(Value v) {
        return new GraphNode(
                v.get("id").asString(),
                v.get("kind").asString(),
                v.get("label").asString(),
                v.get("meta").asString(null));
    }

    private static LocalDate date(Value v) {
        return v.isNull() ? null : v.asLocalDate();
    }

    private static List<String> sorted(List<String> values) {
        List<String> copy = new ArrayList<>(values);
        copy.sort(Comparator.naturalOrder());
        return List.copyOf(copy);
    }
}
