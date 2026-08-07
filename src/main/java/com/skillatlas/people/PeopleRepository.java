package com.skillatlas.people;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import com.skillatlas.people.domain.Person;

// Every read filters out soft-deleted people (CLAUDE.md).
public interface PeopleRepository extends Neo4jRepository<Person, String> {

    Optional<Person> findByIdAndDeletedFalse(String id);

    Optional<Person> findByEmailAndDeletedFalse(String email);

    Page<Person> findByDeletedFalse(Pageable pageable);

    boolean existsByEmailAndDeletedFalse(String email);
}
