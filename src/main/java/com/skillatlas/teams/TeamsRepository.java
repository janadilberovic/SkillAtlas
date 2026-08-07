package com.skillatlas.teams;

import org.springframework.data.neo4j.repository.Neo4jRepository;

import com.skillatlas.teams.domain.Team;

public interface TeamsRepository extends Neo4jRepository<Team, String> {

    boolean existsByName(String name);
}
