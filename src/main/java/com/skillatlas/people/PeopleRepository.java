package com.skillatlas.people;

import java.util.Optional;

import org.springframework.data.neo4j.repository.Neo4jRepository;

import com.skillatlas.people.domain.Person;

// Every read filters out soft-deleted people (CLAUDE.md).
public interface PeopleRepository extends Neo4jRepository<Person, String> {

    Optional<Person> findByIdAndDeletedFalse(String id);

    Optional<Person> findByEmailAndDeletedFalse(String email);

    boolean existsByEmailAndDeletedFalse(String email);

    // Deliberately ignores the soft-delete filter: the unique constraint on Person.email ignores it
    // too, so "can I insert this email?" has to look at deleted rows as well.
    // guard:allow soft-delete - the unique constraint ignores the flag, so this probe must too.
    boolean existsByEmail(String email);
}
