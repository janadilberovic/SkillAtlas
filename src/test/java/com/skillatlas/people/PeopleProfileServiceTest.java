package com.skillatlas.people;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
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
import com.skillatlas.people.dto.PersonProfileResponse;
import com.skillatlas.people.dto.PersonProfileResponse.Mentoring;
import com.skillatlas.people.dto.PersonProfileResponse.Neighbourhood;
import com.skillatlas.people.enums.Role;
import com.skillatlas.people.exception.PersonNotFoundException;
import com.skillatlas.storage.AvatarStorage;

/** Composition rules of the profile service — no database (see PersonProfileIT for the Cypher). */
@ExtendWith(MockitoExtension.class)
class PeopleProfileServiceTest {

    @Mock
    PeopleProfileRepository repository;

    @Mock
    AvatarStorage avatarStorage;

    @InjectMocks
    PeopleProfileService service;

    @Test
    void missingPerson_throwsWithoutAskingForTheNeighbourhood() {
        when(repository.findProfile("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile("ghost"))
                .isInstanceOf(PersonNotFoundException.class);

        verify(repository, never()).neighbourhood(anyString(), anyInt());
    }

    @Test
    void composesTheNeighbourhoodOntoTheProfile_underACap() {
        when(repository.findProfile("p1")).thenReturn(Optional.of(emptyProfile()));
        Neighbourhood neighbourhood = new Neighbourhood(
                List.of(new GraphNode("p1", GraphNodeKind.PERSON, "Ada Lovelace", "Engineer"),
                        new GraphNode("s1", GraphNodeKind.SKILL, "Neo4j", "DATABASE")),
                List.of(new GraphEdge("p1", "s1", "KNOWS")),
                true);
        when(repository.neighbourhood(eq("p1"), anyInt())).thenReturn(neighbourhood);

        PersonProfileResponse profile = service.getProfile("p1");

        assertThat(profile.neighbourhood()).isEqualTo(neighbourhood);
        assertThat(profile.id()).isEqualTo("p1");

        // The subgraph is always bounded — an unbounded one would hand the browser the whole graph.
        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(repository).neighbourhood(eq("p1"), limit.capture());
        assertThat(limit.getValue()).isPositive();
    }

    @Test
    void replacesTheStoredBlobKeyWithASignedUrl() {
        when(repository.findProfile("p1")).thenReturn(Optional.of(emptyProfile()));
        when(repository.neighbourhood(eq("p1"), anyInt()))
                .thenReturn(new Neighbourhood(List.of(), List.of(), false));
        when(avatarStorage.signedUrl("p1/abc.png")).thenReturn("https://blob/p1/abc.png?sig=x");

        assertThat(service.getProfile("p1").avatarUrl()).isEqualTo("https://blob/p1/abc.png?sig=x");
    }

    private static PersonProfileResponse emptyProfile() {
        return new PersonProfileResponse("p1", "ada@test.com", "Ada", "Lovelace", "Engineer",
                Role.MEMBER, true, true, "p1/abc.png", List.of(), List.of(), List.of(), List.of(),
                new Mentoring(List.of(), List.of()),
                new Neighbourhood(List.of(), List.of(), false));
    }
}
