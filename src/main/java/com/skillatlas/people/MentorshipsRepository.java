package com.skillatlas.people;

import java.time.LocalDate;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import com.skillatlas.people.domain.Person;

/** Only the dev seed writes MENTORS today; the admin flow that will is E6.1. */
public interface MentorshipsRepository extends Neo4jRepository<Person, String> {

    // One relationship per (mentor, mentee, skill), and neither side may be soft-deleted.
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
