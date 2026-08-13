package com.skillatlas.people;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;

import com.skillatlas.people.domain.Person;

// The only place KNOWS / WANTS_TO_LEARN relationships are written. Always parameterized ($param).
public interface PeopleSkillsRepository extends Neo4jRepository<Person, String> {

    // Upsert KNOWS: one relationship per (person, skill); keeps the original `since` if present.
    @Query("""
            MATCH (p:Person {id: $personId}) WHERE p.isDeleted = false
            MATCH (s:Skill {id: $skillId})
            MERGE (p)-[r:KNOWS]->(s)
            SET r.level = $level, r.since = coalesce(r.since, $today)
            """)
    void upsertKnows(@Param("personId") String personId,
            @Param("skillId") String skillId,
            @Param("level") int level,
            @Param("today") LocalDate today);

    @Query("MATCH (:Person {id: $personId})-[r:KNOWS]->(:Skill {id: $skillId}) DELETE r")
    void deleteKnows(@Param("personId") String personId, @Param("skillId") String skillId);

    // ON CREATE, not SET: the dev seed re-asserts its fixture on every boot and must not undo a
    // level someone edited in the UI.
    @Query("""
            MATCH (p:Person {id: $personId}) WHERE p.isDeleted = false
            MATCH (s:Skill {id: $skillId})
            MERGE (p)-[r:KNOWS]->(s)
            ON CREATE SET r.level = $level, r.since = $today
            """)
    void insertKnowsIfAbsent(@Param("personId") String personId,
            @Param("skillId") String skillId,
            @Param("level") int level,
            @Param("today") LocalDate today);

    // Level of an existing KNOWS, or null when the person doesn't know the skill.
    @Query("MATCH (:Person {id: $personId})-[r:KNOWS]->(:Skill {id: $skillId}) RETURN r.level")
    Integer knownLevel(@Param("personId") String personId, @Param("skillId") String skillId);

    @Query("""
            MATCH (p:Person {id: $personId}) WHERE p.isDeleted = false
            MATCH (s:Skill {id: $skillId})
            MERGE (p)-[r:WANTS_TO_LEARN]->(s)
            SET r.createdAt = coalesce(r.createdAt, $now)
            """)
    void upsertWish(@Param("personId") String personId,
            @Param("skillId") String skillId,
            @Param("now") Instant now);

    @Query("MATCH (:Person {id: $personId})-[r:WANTS_TO_LEARN]->(:Skill {id: $skillId}) DELETE r")
    void deleteWish(@Param("personId") String personId, @Param("skillId") String skillId);
}
