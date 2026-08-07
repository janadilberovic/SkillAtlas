package com.skillatlas.people.domain;

import java.time.Instant;

import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import com.skillatlas.skills.domain.Skill;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@RelationshipProperties
@Getter
@Setter
@NoArgsConstructor
public class WantsToLearn {

    @RelationshipId
    private Long id;

    private Instant createdAt;

    @TargetNode
    private Skill skill;
}
