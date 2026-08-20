package com.skillatlas.people.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import com.skillatlas.people.enums.Role;
import com.skillatlas.teams.domain.Team;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// level (1..5) lives on the KNOWS relationship (see Knows), never on this node.
@Node("Person")
@Getter
@Setter
@NoArgsConstructor
public class Person {

    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;

    private String email;

    private String passwordHash;

    private Role role;

    private boolean active = true;

    private String firstName;

    private String lastName;

    private String position;

    private String profilePicture;

    private Instant createdAt;

    @Property("isDeleted") // graph property name the read-filters depend on
    private boolean deleted = false;

    private Instant deletedAt;

    @Relationship(type = "KNOWS")
    private Set<Knows> knows = new HashSet<>();

    @Relationship(type = "WANTS_TO_LEARN")
    private Set<WantsToLearn> wantsToLearn = new HashSet<>();

    @Relationship(type = "WORKED_ON")
    private Set<WorkedOn> workedOn = new HashSet<>();

    @Relationship(type = "MEMBER_OF")
    private Set<Team> teams = new HashSet<>();

    @Relationship(type = "MENTORS")
    private Set<Mentors> mentees = new HashSet<>();
}
