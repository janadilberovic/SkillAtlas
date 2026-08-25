package com.skillatlas.people;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

/**
 * The two queries the VacaYAY import needs (E3). Two, not two-per-person: a roster of 200 costs the
 * same round trips as a roster of 2.
 *
 * <p>{@link Neo4jClient} rather than a derived repository for the same reason as
 * {@link PeopleSearchRepository} — the merge returns a projection of what it did, not a
 * {@code Person} entity.
 */
@Repository
public class PeopleImportRepository {

    // toLower on both sides: the unique constraint on Person.email is case-sensitive, so
    // Nina.Hodzic@ing.com and nina.hodzic@ing.com are two legal nodes for one human being.
    // guard:allow soft-delete - "is this email free?" must see deleted people, exactly like
    // PeopleRepository.existsByEmail, because the constraint sees them too.
    private static final String TAKEN_EMAILS = """
            MATCH (p:Person) WHERE toLower(p.email) IN $emails
            RETURN toLower(p.email) AS email
            """;

    // MERGE, not CREATE (CLAUDE.md): a double-clicked import must not produce two nodes.
    // ON CREATE only: a position an admin fixed by hand is not overwritten by the old system on
    // the next run, so pressing Import twice is a no-op rather than a rollback.
    // guard:allow soft-delete - the MERGE has to match deleted people; skipping them would hand
    // them a duplicate node, and their isDeleted flag stays untouched precisely because nothing
    // outside ON CREATE writes to the node.
    private static final String MERGE_PEOPLE = """
            UNWIND $rows AS row
            MERGE (p:Person {email: row.email})
            ON CREATE SET p.id = row.newId,
                          p.firstName = row.firstName,
                          p.lastName = row.lastName,
                          p.position = row.position,
                          p.role = 'MEMBER',
                          p.active = row.active,
                          p.isDeleted = false,
                          p.createdAt = $now
            RETURN row.newId AS newId, p.id AS id, p.email AS email, p.firstName AS firstName,
                   p.lastName AS lastName, p.position AS position, p.active AS active
            """;

    private final Neo4jClient client;

    public PeopleImportRepository(Neo4jClient client) {
        this.client = client;
    }

    /** @param emails lowercased addresses to probe; the returned set is lowercased too */
    public Set<String> takenEmails(List<String> emails) {
        if (emails.isEmpty()) {
            return Set.of();
        }
        return client.query(TAKEN_EMAILS)
                .bindAll(Map.of("emails", emails))
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("email").asString())
                .all()
                .stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * @param rows one map per person, carrying the UUID minted for it — see
     *             {@link MergedRow#created()} for why the caller brings its own id
     * @return one row per input row, in no guaranteed order
     */
    public List<MergedRow> mergePeople(List<Map<String, Object>> rows, ZonedDateTime now) {
        return List.copyOf(client.query(MERGE_PEOPLE)
                .bindAll(Map.of("rows", rows, "now", now))
                .fetchAs(MergedRow.class)
                .mappedBy((typeSystem, record) -> new MergedRow(
                        record.get("newId").asString(),
                        record.get("id").asString(),
                        record.get("email").asString(),
                        record.get("firstName").asString(null),
                        record.get("lastName").asString(null),
                        record.get("position").asString(null),
                        record.get("active").asBoolean(true)))
                .all());
    }

    /**
     * What the MERGE did with one row.
     *
     * @param newId the UUID this run minted for the row; the node keeps it only if ON CREATE ran
     */
    public record MergedRow(String newId, String id, String email, String firstName,
            String lastName, String position, boolean active) {

        /**
         * MERGE does not report whether it matched or created, so the id does it instead: a node
         * that already existed carries an older UUID. Comparing timestamps would work too, right up
         * until two imports run in the same millisecond.
         */
        public boolean created() {
            return newId.equals(id);
        }
    }
}
