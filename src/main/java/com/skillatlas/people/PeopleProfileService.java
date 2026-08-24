package com.skillatlas.people;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skillatlas.people.dto.PersonProfileResponse;
import com.skillatlas.people.exception.PersonNotFoundException;
import com.skillatlas.storage.AvatarStorage;

@Service
public class PeopleProfileService {

    private static final int NEIGHBOURHOOD_LIMIT = 60;

    private final PeopleProfileRepository repository;
    private final AvatarStorage avatarStorage;

    public PeopleProfileService(PeopleProfileRepository repository, AvatarStorage avatarStorage) {
        this.repository = repository;
        this.avatarStorage = avatarStorage;
    }

    @Transactional(readOnly = true)
    public PersonProfileResponse getProfile(String id) {
        PersonProfileResponse profile = repository.findProfile(id)
                .orElseThrow(() -> new PersonNotFoundException(id));
        return profile.withAvatarUrl(avatarStorage.signedUrl(profile.avatarUrl()))
                .withNeighbourhood(repository.neighbourhood(id, NEIGHBOURHOOD_LIMIT));
    }
}
