package com.skillatlas.people;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skillatlas.people.dto.PersonProfileResponse;
import com.skillatlas.people.exception.PersonNotFoundException;

/**
 * E4.2 — assembles the profile behind {@code GET /api/v1/people/{id}}.
 *
 * <p>Two queries, both bounded: the aggregate itself and the capped neighbourhood. Constant, not
 * per-row — nothing here calls the database inside a loop.
 */
@Service
public class PeopleProfileService {

    /** Relationships drawn around the person before the subgraph is reported as truncated. */
    private static final int NEIGHBOURHOOD_LIMIT = 60;

    private final PeopleProfileRepository repository;

    public PeopleProfileService(PeopleProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PersonProfileResponse getProfile(String id) {
        PersonProfileResponse profile = repository.findProfile(id)
                .orElseThrow(() -> new PersonNotFoundException(id));
        return profile.withNeighbourhood(repository.neighbourhood(id, NEIGHBOURHOOD_LIMIT));
    }
}
