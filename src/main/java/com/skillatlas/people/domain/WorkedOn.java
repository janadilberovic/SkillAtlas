package com.skillatlas.people.domain;

import java.time.LocalDate;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import com.skillatlas.projects.domain.Project;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Kept even for soft-deleted people: a project still knows who worked on it (CLAUDE.md).
@RelationshipProperties
@Getter
@Setter
@NoArgsConstructor
public class WorkedOn {

    @RelationshipId
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;

    private String role;

    private LocalDate from;

    private LocalDate to;

    @TargetNode
    private Project project;
}
