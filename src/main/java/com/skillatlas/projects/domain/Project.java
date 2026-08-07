package com.skillatlas.projects.domain;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import com.skillatlas.skills.domain.Skill;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Node("Project")
@Getter
@Setter
@NoArgsConstructor
public class Project {

    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;

    private String name;

    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    private boolean active = true;

    @Relationship(type = "USES")
    private Set<Skill> uses = new HashSet<>();
}
