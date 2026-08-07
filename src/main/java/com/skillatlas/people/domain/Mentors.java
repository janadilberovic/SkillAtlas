package com.skillatlas.people.domain;

import java.time.LocalDate;

import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Person -[MENTORS {skillId, since}]-> Person. Created only after an admin confirms.
// skillId references the Skill this mentorship is about (kept as a scalar; a
// relationship-properties class cannot itself hold a relationship to Skill).
@RelationshipProperties
@Getter
@Setter
@NoArgsConstructor
public class Mentors {

    @RelationshipId
    private Long id;

    private String skillId;

    private LocalDate since;

    @TargetNode
    private Person mentee;
}
