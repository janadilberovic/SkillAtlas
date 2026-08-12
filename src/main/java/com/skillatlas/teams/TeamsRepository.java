package com.skillatlas.teams;

import java.util.Optional;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import com.skillatlas.teams.domain.Team;

public interface TeamsRepository extends Neo4jRepository<Team, String> {

    boolean existsByName(String name);

    Optional<Team> findByName(String name);

    // MERGE, not CREATE: joining the same team twice must not stack MEMBER_OF edges.
    @Query("""
            MATCH (p:Person {id: $personId}) WHERE p.isDeleted = false
            MATCH (t:Team {id: $teamId})
            MERGE (p)-[:MEMBER_OF]->(t)
            """)
    void addMember(@Param("teamId") String teamId, @Param("personId") String personId);
}
