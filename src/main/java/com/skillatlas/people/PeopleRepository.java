package com.skillatlas.people;

import java.util.Optional;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

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

    // A targeted SET rather than save(person): saving the entity would rewrite the whole loaded
    // aggregate (KNOWS, WORKED_ON, MEMBER_OF...) to change one string. A null key clears it.
    @Query("MATCH (p:Person {id: $id}) WHERE p.isDeleted = false SET p.profilePicture = $key")
    void updateProfilePicture(@Param("id") String id, @Param("key") String key);
}
