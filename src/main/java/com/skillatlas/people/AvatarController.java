package com.skillatlas.people;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.skillatlas.people.dto.AvatarResponse;
import com.skillatlas.security.SecurityUtil;

@RestController
@RequestMapping("/api/v1/people/{id}/avatar")
public class AvatarController {

    private final AvatarService service;

    public AvatarController(AvatarService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AvatarResponse upload(@PathVariable String id, @RequestPart("file") MultipartFile file) {
        requireSelf(id);
        return service.upload(id, file);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String id) {
        requireSelf(id);
        service.remove(id);
    }

    // IDOR guard: your own photo only, admins included. Identity comes from the token, never the
    // path. Unlike PeopleSkillsController this has no admin escape hatch — nobody edits someone
    // else's picture.
    private void requireSelf(String id) {
        if (!id.equals(SecurityUtil.currentUserId())) {
            throw new AccessDeniedException("You can only change your own photo");
        }
    }
}
