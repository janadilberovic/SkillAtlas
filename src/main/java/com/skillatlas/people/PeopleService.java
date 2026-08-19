package com.skillatlas.people;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skillatlas.people.domain.Person;
import com.skillatlas.people.dto.PersonCreateRequest;
import com.skillatlas.people.dto.PersonUpdateRequest;
import com.skillatlas.people.exception.EmailAlreadyExistsException;
import com.skillatlas.people.exception.PersonNotFoundException;
import com.skillatlas.people.exception.SelfDeleteNotAllowedException;
import com.skillatlas.security.SecurityUtil;

@Service
public class PeopleService {

    private final PeopleRepository repository;
    private final PasswordEncoder passwordEncoder;

    public PeopleService(PeopleRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Person getById(String id) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new PersonNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<Person> list(Pageable pageable) {
        return repository.findByDeletedFalse(pageable);
    }

    @Transactional
    public Person create(PersonCreateRequest request) {
        // Deliberately ignores the soft-delete flag: the unique constraint on Person.email does
        // too, so an email freed by a soft delete would pass this check and then blow up as a
        // constraint violation (500) instead of a 409.
        if (repository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }
        Person person = new Person();
        person.setEmail(request.email());
        person.setPasswordHash(passwordEncoder.encode(request.password()));
        person.setFirstName(request.firstName());
        person.setLastName(request.lastName());
        person.setPosition(request.position());
        person.setProfilePicture(request.profilePicture());
        person.setRole(request.role());
        person.setActive(true);
        return repository.save(person);
    }

    @Transactional
    public Person updateProfile(String id, PersonUpdateRequest request) {
        Person person = getById(id);
        person.setFirstName(request.firstName());
        person.setLastName(request.lastName());
        person.setPosition(request.position());
        person.setProfilePicture(request.profilePicture());
        return repository.save(person);
    }

    @Transactional
    public void softDelete(String id) {
        // An admin who deletes themselves keeps a valid token for a person every read filters out,
        // so the next GET /me 404s and the client falls apart with no way back.
        if (id.equals(SecurityUtil.currentUserId())) {
            throw new SelfDeleteNotAllowedException();
        }
        Person person = getById(id);
        person.setDeleted(true);
        person.setDeletedAt(Instant.now());
        repository.save(person);
    }
}
