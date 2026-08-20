package com.skillatlas.people;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.skillatlas.people.domain.Person;
import com.skillatlas.people.dto.PersonCreateRequest;
import com.skillatlas.people.dto.PersonResponse;
import com.skillatlas.people.dto.PersonUpdateRequest;
import com.skillatlas.people.exception.EmailAlreadyExistsException;
import com.skillatlas.people.exception.PersonNotFoundException;
import com.skillatlas.people.exception.SelfDeleteNotAllowedException;
import com.skillatlas.security.SecurityUtil;

@Service
public class PeopleService {

    private final PeopleRepository repository;
    private final PeopleSearchRepository searchRepository;
    private final PasswordEncoder passwordEncoder;

    public PeopleService(PeopleRepository repository, PeopleSearchRepository searchRepository,
            PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.searchRepository = searchRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Person getById(String id) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new PersonNotFoundException(id));
    }

    /** Each filter is optional; blank is "no filter". Ordering lives in the query, not the pageable. */
    @Transactional(readOnly = true)
    public Page<PersonResponse> list(String search, String team, String skill, Pageable pageable) {
        String s = normalise(search);
        String t = normalise(team);
        String k = normalise(skill);
        List<PersonResponse> content =
                searchRepository.find(s, t, k, pageable.getOffset(), pageable.getPageSize());
        return new PageImpl<>(content, pageable, searchRepository.count(s, t, k));
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
        person.setCreatedAt(Instant.now());
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

    // The query compares lowercased, so the caller's casing and padding are normalised here.
    private static String normalise(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }
}
