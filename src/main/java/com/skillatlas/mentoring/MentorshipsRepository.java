package com.skillatlas.mentoring;

import java.time.LocalDate;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import com.skillatlas.people.domain.Person;

/** Every write to MENTORS. The relationship exists only once an admin confirms a candidate (§4.3). */
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

    // count() aggregates over an empty match too, so a missing mentorship is false, not no row.
    // guard:allow soft-delete - a boolean about the edge on the teardown path; remove() deletes right after,
    // and tearing down a mentorship whose mentor was since soft-deleted is the wanted behaviour, not a 404.
    @Query("""
            MATCH (:Person {id: $mentorId})-[r:MENTORS {skillId: $skillId}]->(:Person {id: $menteeId})
            RETURN count(r) > 0
            """)
    boolean existsMentorship(@Param("mentorId") String mentorId,
            @Param("menteeId") String menteeId,
            @Param("skillId") String skillId);

    @Query("""
            MATCH (:Person {id: $mentorId})-[r:MENTORS {skillId: $skillId}]->(:Person {id: $menteeId})
            DELETE r
            """)
    void deleteMentorship(@Param("mentorId") String mentorId,
            @Param("menteeId") String menteeId,
            @Param("skillId") String skillId);
}
