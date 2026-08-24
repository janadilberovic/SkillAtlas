package com.skillatlas.people;

import java.io.IOException;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.skillatlas.people.domain.Person;
import com.skillatlas.people.dto.AvatarResponse;
import com.skillatlas.people.exception.PersonNotFoundException;
import com.skillatlas.storage.AvatarStorage;
import com.skillatlas.storage.ImageType;
import com.skillatlas.storage.exception.ImageTooLargeException;
import com.skillatlas.storage.exception.UnsupportedImageTypeException;

@Service
public class AvatarService {

    static final long MAX_BYTES = 2L * 1024 * 1024;

    private final PeopleRepository repository;
    private final AvatarStorage storage;

    public AvatarService(PeopleRepository repository, AvatarStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Transactional
    public AvatarResponse upload(String personId, MultipartFile file) {
        byte[] bytes = read(file);
        if (bytes.length > MAX_BYTES) {
            throw new ImageTooLargeException(MAX_BYTES);
        }
        ImageType type = ImageType.sniff(bytes);
        if (type == null) {
            throw new UnsupportedImageTypeException();
        }
        Person person = repository.findByIdAndDeletedFalse(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));

        String previous = person.getProfilePicture();
        String key = storage.store(personId, bytes, type);
        repository.updateProfilePicture(personId, key);
        // Only after the new key is committed. The other order would leave the person pointing at a
        // blob that no longer exists if the write failed — a broken picture with no way back.
        if (previous != null) {
            storage.delete(previous);
        }
        return new AvatarResponse(storage.signedUrl(key), Instant.now().plus(storage.urlTtl()));
    }

    @Transactional
    public void remove(String personId) {
        Person person = repository.findByIdAndDeletedFalse(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        repository.updateProfilePicture(personId, null);
        storage.delete(person.getProfilePicture());
    }

    private static byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new UnsupportedImageTypeException();
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UnsupportedImageTypeException();
        }
    }
}
