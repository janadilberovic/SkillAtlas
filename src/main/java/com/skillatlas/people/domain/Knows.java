package com.skillatlas.people.domain;

import java.time.LocalDate;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import com.skillatlas.skills.domain.Skill;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Person -[KNOWS {level, since}]-> Skill. level 1..5 is validated at the app edge.
@RelationshipProperties
@Getter
@Setter
@NoArgsConstructor
public class Knows {

    @RelationshipId
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;

    private int level;

    private LocalDate since;

    @TargetNode
    private Skill skill;
}
