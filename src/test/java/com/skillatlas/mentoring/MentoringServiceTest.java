package com.skillatlas.mentoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.skillatlas.graph.dto.GraphEdge;
import com.skillatlas.graph.dto.GraphNode;
import com.skillatlas.graph.enums.GraphNodeKind;
import com.skillatlas.mentoring.dto.LearningPathResponse;
import com.skillatlas.mentoring.dto.MentorshipRequest;
import com.skillatlas.mentoring.dto.SkillRef;
import com.skillatlas.mentoring.exception.InvalidMentorshipException;
import com.skillatlas.mentoring.exception.MentorshipNotFoundException;
import com.skillatlas.people.PeopleRepository;
import com.skillatlas.people.PeopleSkillsRepository;
import com.skillatlas.people.domain.Person;
import com.skillatlas.people.exception.PersonNotFoundException;
import com.skillatlas.skills.SkillsRepository;
import com.skillatlas.skills.domain.Skill;
import com.skillatlas.skills.exception.SkillNotFoundException;

// The mentoring rules from spec 4.3 / 4.4, with mocked repositories (no database).
@ExtendWith(MockitoExtension.class)
class MentoringServiceTest {

    private static final String MENTOR = "mentor-1";
    private static final String MENTEE = "mentee-1";
    private static final String SKILL = "skill-1";

    @Mock
    MentoringRepository repository;
    @Mock
    MentorshipsRepository mentorships;
    @Mock
    PeopleRepository people;
    @Mock
    PeopleSkillsRepository peopleSkills;
    @Mock
    SkillsRepository skills;
    @InjectMocks
    MentoringService service;

    @Test
    void skillName_isTrimmedAndLowercasedBeforeLookup() {
        person(MENTEE, "Bob", "Byte");
        when(repository.findSkillByName("neo4j")).thenReturn(Optional.of(new SkillRef(SKILL, "Neo4j")));
        when(repository.mentorCandidates(eq(MENTEE), eq(SKILL), anyInt(), anyInt())).thenReturn(List.of());

        service.mentorCandidates(MENTEE, "  Neo4J ", null);

        verify(repository).findSkillByName("neo4j");
    }

    @Test
    void candidateSearch_appliesTheLevelBarAndClampsTheLimit() {
        person(MENTEE, "Bob", "Byte");
        when(repository.findSkillByName(anyString())).thenReturn(Optional.of(new SkillRef(SKILL, "Neo4j")));
        when(repository.mentorCandidates(anyString(), anyString(), anyInt(), anyInt())).thenReturn(List.of());

        service.mentorCandidates(MENTEE, "neo4j", 9999);

        ArgumentCaptor<Integer> minLevel = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(repository).mentorCandidates(eq(MENTEE), eq(SKILL), minLevel.capture(), limit.capture());
        assertThat(minLevel.getValue()).isEqualTo(MentoringService.MIN_MENTOR_LEVEL);
        assertThat(limit.getValue()).isEqualTo(MentoringService.MAX_CANDIDATE_LIMIT);
    }

    @Test
    void unknownSkill_isNotFound() {
        person(MENTEE, "Bob", "Byte");
        when(repository.findSkillByName(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.mentorCandidates(MENTEE, "cobol", null))
                .isInstanceOf(SkillNotFoundException.class);
    }

    @Test
    void softDeletedOrMissingMentee_isNotFound() {
        when(people.findByIdAndDeletedFalse(MENTEE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.mentorCandidates(MENTEE, "neo4j", null))
                .isInstanceOf(PersonNotFoundException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void nobodyMentorsThemselves_andNothingIsRead() {
        assertThatThrownBy(() -> service.confirm(new MentorshipRequest(MENTOR, MENTOR, SKILL)))
                .isInstanceOf(InvalidMentorshipException.class);

        verifyNoInteractions(people, mentorships, repository);
    }

    @Test
    void mentorBelowTheLevelBar_isRejectedAtTheWriteToo() {
        person(MENTOR, "Ada", "Lovelace");
        person(MENTEE, "Bob", "Byte");
        skill();
        // 2 < 3: the candidate list would never have offered them, but a stale client might.
        when(peopleSkills.knownLevel(MENTOR, SKILL)).thenReturn(2);

        assertThatThrownBy(() -> service.confirm(new MentorshipRequest(MENTOR, MENTEE, SKILL)))
                .isInstanceOf(InvalidMentorshipException.class);
        verify(mentorships, never()).upsertMentorship(any(), any(), any(), any());
    }

    @Test
    void mentorWhoDoesNotKnowTheSkillAtAll_isRejected() {
        person(MENTOR, "Ada", "Lovelace");
        person(MENTEE, "Bob", "Byte");
        skill();
        when(peopleSkills.knownLevel(MENTOR, SKILL)).thenReturn(null);

        assertThatThrownBy(() -> service.confirm(new MentorshipRequest(MENTOR, MENTEE, SKILL)))
                .isInstanceOf(InvalidMentorshipException.class);
        verify(mentorships, never()).upsertMentorship(any(), any(), any(), any());
    }

    @Test
    void confirmation_stampsSinceOnTheServer() {
        person(MENTOR, "Ada", "Lovelace");
        person(MENTEE, "Bob", "Byte");
        skill();
        when(peopleSkills.knownLevel(MENTOR, SKILL)).thenReturn(5);

        var response = service.confirm(new MentorshipRequest(MENTOR, MENTEE, SKILL));

        verify(mentorships).upsertMentorship(MENTOR, MENTEE, SKILL, LocalDate.now());
        assertThat(response.since()).isEqualTo(LocalDate.now());
        assertThat(response.mentorName()).isEqualTo("Ada Lovelace");
        assertThat(response.skill().name()).isEqualTo("Neo4j");
    }

    @Test
    void removingAMentorshipThatIsNotThere_isNotFound() {
        when(mentorships.existsMentorship(MENTOR, MENTEE, SKILL)).thenReturn(false);

        assertThatThrownBy(() -> service.remove(MENTOR, MENTEE, SKILL))
                .isInstanceOf(MentorshipNotFoundException.class);
        verify(mentorships, never()).deleteMentorship(any(), any(), any());
    }

    @Test
    void noPath_isAnAnswerNotAnError() {
        person(MENTEE, "Bob", "Byte");
        when(repository.findSkillByName(anyString())).thenReturn(Optional.of(new SkillRef(SKILL, "Neo4j")));
        when(repository.learningPath(eq(MENTEE), any(), anyInt())).thenReturn(Optional.empty());

        LearningPathResponse path = service.learningPath(MENTEE, "neo4j");

        assertThat(path.found()).isFalse();
        assertThat(path.nodes()).isEmpty();
        assertThat(path.nearestMentor()).isNull();
    }

    @Test
    void nearestMentor_isTheFirstOneOnTheWalkNotTheStrongest() {
        person(MENTEE, "Bob", "Byte");
        when(repository.findSkillByName(anyString())).thenReturn(Optional.of(new SkillRef(SKILL, "Neo4j")));
        when(repository.learningPath(eq(MENTEE), any(), anyInt()))
                .thenReturn(Optional.of(walkThrough("carl", "ada")));
        // Ada is the stronger mentor, but Carl stands between Bob and her.
        when(repository.mentorsAmong(List.of("carl", "ada"), SKILL, MentoringService.MIN_MENTOR_LEVEL))
                .thenReturn(Map.of(
                        "carl", new LearningPathResponse.NearestMentor("carl", "Carl Cache", 3),
                        "ada", new LearningPathResponse.NearestMentor("ada", "Ada Lovelace", 5)));

        LearningPathResponse path = service.learningPath(MENTEE, "neo4j");

        assertThat(path.nearestMentor().id()).isEqualTo("carl");
    }

    @Test
    void aWalkWithNobodyOnIt_doesNotAskForMentors() {
        person(MENTEE, "Bob", "Byte");
        when(repository.findSkillByName(anyString())).thenReturn(Optional.of(new SkillRef(SKILL, "Neo4j")));
        when(repository.learningPath(eq(MENTEE), any(), anyInt()))
                .thenReturn(Optional.of(walkThrough()));

        LearningPathResponse path = service.learningPath(MENTEE, "neo4j");

        assertThat(path.nearestMentor()).isNull();
        verify(repository, never()).mentorsAmong(any(), any(), anyInt());
    }

    private LearningPathResponse walkThrough(String... peerIds) {
        List<GraphNode> nodes = new ArrayList<>();
        nodes.add(new GraphNode(MENTEE, GraphNodeKind.PERSON, "Bob Byte", null));
        for (String peer : peerIds) {
            nodes.add(new GraphNode(peer, GraphNodeKind.PERSON, peer, null));
        }
        nodes.add(new GraphNode(SKILL, GraphNodeKind.SKILL, "Neo4j", "DATABASE"));
        return new LearningPathResponse(MENTEE, new SkillRef(SKILL, "Neo4j"), true,
                nodes.size() - 1, null, List.copyOf(nodes), List.<GraphEdge>of(), null);
    }

    private void person(String id, String first, String last) {
        Person person = new Person();
        person.setId(id);
        person.setFirstName(first);
        person.setLastName(last);
        when(people.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(person));
    }

    private void skill() {
        Skill skill = new Skill();
        skill.setId(SKILL);
        skill.setName("Neo4j");
        when(skills.findById(SKILL)).thenReturn(Optional.of(skill));
    }
}
