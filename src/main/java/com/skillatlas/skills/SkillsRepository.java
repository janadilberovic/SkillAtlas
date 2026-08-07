package com.skillatlas.skills;

import org.springframework.data.neo4j.repository.Neo4jRepository;

import com.skillatlas.skills.domain.Skill;

public interface SkillsRepository extends Neo4jRepository<Skill, String> {

    boolean existsByName(String name);
}
