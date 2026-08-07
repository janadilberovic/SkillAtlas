package com.skillatlas.skills.domain;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import com.skillatlas.skills.enums.SkillCategory;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Node("Skill")
@Getter
@Setter
@NoArgsConstructor
public class Skill {

    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;

    private String name; // unique constraint enforced in Neo4j (CLAUDE.md)

    private SkillCategory category;

    private String color; // optional, for graph visualization
}
