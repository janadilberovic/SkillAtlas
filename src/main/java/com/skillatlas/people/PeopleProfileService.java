package com.skillatlas.people;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skillatlas.people.dto.PersonProfileResponse;
import com.skillatlas.people.exception.PersonNotFoundException;

@Service
public class PeopleProfileService {

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
