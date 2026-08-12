package com.skillatlas.people;

import java.time.LocalDate;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import com.skillatlas.people.domain.Person;

/**
 * Writes of the MENTORS relationship. The read side lives in the profile query
 * ({@link PeopleProfileRepository}); the admin-facing mentor matching that will call this from a
 * controller is E6.1 — today only the dev seed does.
 */
public interface MentorshipsRepository extends Neo4jRepository<Person, String> {

    // One relationship per (mentor, mentee, skill): MERGE, so re-running the seed adds nothing.
    // Neither side may be soft-deleted.
    @Query("""
            MATCH (mentor:Person {id: $mentorId}) WHERE mentor.isDeleted = false
            MATCH (mentee:Person {id: $menteeId}) WHERE mentee.isDeleted = false
            MERGE (mentor)-[r:MENTORS {skillId: $skillId}]->(mentee)
            SET r.since = coalesce(r.since, $since)
            """)
    void upsertMentorship(@Param("mentorId") String mentorId,
            @Param("menteeId") String menteeId,
            @Param("skillId") String skillId,
            @Param("since") LocalDate since);
}
