package com.skillatlas.people;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skillatlas.people.domain.Person;
import com.skillatlas.people.dto.MySkillsResponse;
import com.skillatlas.people.exception.PersonNotFoundException;
import com.skillatlas.people.exception.SkillAlreadyMasteredException;
import com.skillatlas.skills.SkillsRepository;
import com.skillatlas.skills.exception.SkillNotFoundException;

@Service
public class PeopleSkillsService {

    private static final int MAX_LEVEL = 5;

    private final PeopleRepository peopleRepository;
    private final PeopleSkillsRepository peopleSkillsRepository;
    private final SkillsRepository skillsRepository;

    public PeopleSkillsService(PeopleRepository peopleRepository,
            PeopleSkillsRepository peopleSkillsRepository,
            SkillsRepository skillsRepository) {
        this.peopleRepository = peopleRepository;
        this.peopleSkillsRepository = peopleSkillsRepository;
        this.skillsRepository = skillsRepository;
    }

    @Transactional(readOnly = true)
    public MySkillsResponse getMySkills(String personId) {
        return MySkillsResponse.from(requirePerson(personId));
    }

    @Transactional
    public MySkillsResponse setSkillLevel(String personId, String skillId, int level) {
        requirePerson(personId);
        requireSkill(skillId);
        peopleSkillsRepository.upsertKnows(personId, skillId, level, LocalDate.now());
        // §4.1: once mastered, a pending "want to learn" for the same skill no longer makes sense.
        if (level == MAX_LEVEL) {
            peopleSkillsRepository.deleteWish(personId, skillId);
        }
        return loadMySkills(personId);
    }

    @Transactional
    public MySkillsResponse removeSkill(String personId, String skillId) {
        requirePerson(personId);
        peopleSkillsRepository.deleteKnows(personId, skillId);
        return loadMySkills(personId);
    }

    @Transactional
    public MySkillsResponse addWish(String personId, String skillId) {
        requirePerson(personId);
        requireSkill(skillId);
        // §4.1: can't want to learn a skill already known at the maximum level.
        Integer known = peopleSkillsRepository.knownLevel(personId, skillId);
        if (known != null && known == MAX_LEVEL) {
            throw new SkillAlreadyMasteredException(skillId);
        }
        peopleSkillsRepository.upsertWish(personId, skillId, Instant.now());
        return loadMySkills(personId);
    }

    @Transactional
    public MySkillsResponse removeWish(String personId, String skillId) {
        requirePerson(personId);
        peopleSkillsRepository.deleteWish(personId, skillId);
        return loadMySkills(personId);
    }

    private MySkillsResponse loadMySkills(String personId) {
        return MySkillsResponse.from(requirePerson(personId));
    }

    private Person requirePerson(String personId) {
        return peopleRepository.findByIdAndDeletedFalse(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
    }

    private void requireSkill(String skillId) {
        if (!skillsRepository.existsById(skillId)) {
            throw new SkillNotFoundException(skillId);
        }
    }
}
