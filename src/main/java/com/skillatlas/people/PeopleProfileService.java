package com.skillatlas.people;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skillatlas.people.dto.PersonProfileResponse;
import com.skillatlas.people.exception.PersonNotFoundException;
import com.skillatlas.security.SecurityUtil;
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
        return profile
                // Whether an account can be signed into is admin business; the query fetches it
                // for everyone, this drops it again for everyone else.
                .withHasPassword(SecurityUtil.currentUserIsAdmin() ? profile.hasPassword() : null)
                .withAvatarUrl(avatarStorage.signedUrl(profile.avatarUrl()))
                .withNeighbourhood(repository.neighbourhood(id, NEIGHBOURHOOD_LIMIT));
    }
}
