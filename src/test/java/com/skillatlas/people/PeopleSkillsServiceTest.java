package com.skillatlas.people;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.skillatlas.people.domain.Person;
import com.skillatlas.people.exception.SkillAlreadyMasteredException;
import com.skillatlas.skills.SkillsRepository;

// Unit tests for the §4.1 business rules, with mocked repositories (no database).
@ExtendWith(MockitoExtension.class)
class PeopleSkillsServiceTest {

    @Mock
    PeopleRepository peopleRepository;
    @Mock
    PeopleSkillsRepository peopleSkillsRepository;
    @Mock
    SkillsRepository skillsRepository;
    @InjectMocks
    PeopleSkillsService service;

    Person person;

    @BeforeEach
    void setup() {
        person = new Person();
        person.setId("p1");
        when(peopleRepository.findByIdAndDeletedFalse("p1")).thenReturn(Optional.of(person));
        when(skillsRepository.existsById("s1")).thenReturn(true);
    }

    @Test
    void addWish_rejectsWhenAlreadyMasteredAtLevel5() {
        when(peopleSkillsRepository.knownLevel("p1", "s1")).thenReturn(5);

        assertThatThrownBy(() -> service.addWish("p1", "s1"))
                .isInstanceOf(SkillAlreadyMasteredException.class);

        verify(peopleSkillsRepository, never()).upsertWish(any(), any(), any());
    }

    @Test
    void addWish_allowedWhenKnownBelowMax() {
        when(peopleSkillsRepository.knownLevel("p1", "s1")).thenReturn(3);

        service.addWish("p1", "s1");

        verify(peopleSkillsRepository).upsertWish(eq("p1"), eq("s1"), any());
    }

    @Test
    void setSkillLevel5_convertsAwayTheWish() {
        service.setSkillLevel("p1", "s1", 5);

        verify(peopleSkillsRepository).upsertKnows(eq("p1"), eq("s1"), eq(5), any());
        verify(peopleSkillsRepository).deleteWish("p1", "s1");
    }

    @Test
    void setSkillLevelBelowMax_keepsTheWish() {
        service.setSkillLevel("p1", "s1", 3);

        verify(peopleSkillsRepository).upsertKnows(eq("p1"), eq("s1"), eq(3), any());
        verify(peopleSkillsRepository, never()).deleteWish(any(), any());
    }
}
