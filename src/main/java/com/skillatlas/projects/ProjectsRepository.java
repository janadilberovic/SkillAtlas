package com.skillatlas.projects;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import com.skillatlas.projects.domain.Project;

public interface ProjectsRepository extends Neo4jRepository<Project, String> {

    Page<Project> findByNameContainingIgnoreCase(String name, Pageable pageable);

    // WORKED_ON is owned by Person; assign/unassign a member via parameterized Cypher.
    // Soft-deleted people can't be assigned; MERGE keeps one relationship per (person, project).
    @Query("""
            MATCH (person:Person {id: $personId}) WHERE person.isDeleted = false
            MATCH (project:Project {id: $projectId})
            MERGE (person)-[r:WORKED_ON]->(project)
            SET r.role = $role, r.from = $from, r.to = $to
            """)
    void assignMember(@Param("projectId") String projectId,
            @Param("personId") String personId,
            @Param("role") String role,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            MATCH (:Person {id: $personId})-[r:WORKED_ON]->(:Project {id: $projectId})
            DELETE r
            """)
    void removeMember(@Param("projectId") String projectId, @Param("personId") String personId);
}
