package com.skillatlas.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.skillatlas.graph.dto.GraphResponse;
import com.skillatlas.graph.enums.GraphNodeKind;
import com.skillatlas.graph.exception.InvalidNodeTypeException;

/** Parameter handling of the explorer — no database (see GraphIT for the Cypher). */
@ExtendWith(MockitoExtension.class)
class GraphServiceTest {

    @Mock
    GraphRepository repository;

    @InjectMocks
    GraphService service;

    @BeforeEach
    void stubRepository() {
        lenient().when(repository.seedPeople(any(), anyInt(), any(), anyInt()))
                .thenReturn(List.of("p1"));
        lenient().when(repository.subgraph(anyList(), any(), anyInt()))
                .thenReturn(new GraphResponse(List.of(), List.of(), 0, Map.of(), false));
        lenient().when(repository.totals()).thenReturn(Map.of(GraphNodeKind.PERSON, 7L));
    }

    @Test
    void noTypes_meansEveryKind() {
        service.explore(null, null, null, null, null);

        assertThat(capturedKinds()).containsExactlyInAnyOrder(GraphNodeKind.values());
    }

    @Test
    void typesAreCaseInsensitive() {
        service.explore(List.of("Person", "SKILL"), null, null, null, null);

        assertThat(capturedKinds())
                .containsExactlyInAnyOrder(GraphNodeKind.PERSON, GraphNodeKind.SKILL);
    }

    @Test
    void unknownType_isRejectedBeforeTheDatabaseIsTouched() {
        assertThatThrownBy(() -> service.explore(List.of("person", "dragon"), null, null, null, null))
                .isInstanceOf(InvalidNodeTypeException.class)
                .hasMessageContaining("dragon");

        verify(repository, never()).seedPeople(any(), anyInt(), any(), anyInt());
    }

    @Test
    void limitIsClampedToTheServersCeiling() {
        service.explore(null, null, null, null, 100_000);

        verify(repository).subgraph(anyList(), any(), eq(GraphService.MAX_LIMIT));
    }

    @Test
    void nonPositiveLimit_fallsBackToOneRatherThanReturningEverything() {
        service.explore(null, null, null, null, 0);

        verify(repository).subgraph(anyList(), any(), eq(1));
    }

    @Test
    void hopsIsClampedToTheDepthsTheQuerySupports() {
        service.explore(null, null, "p1", 99, null);

        verify(repository).seedPeople(eq("p1"), eq(GraphService.MAX_HOPS), any(), anyInt());
    }

    @Test
    void teamIsLowercasedAndBlankIsDropped() {
        service.explore(null, "  Backend  ", null, null, null);
        verify(repository).seedPeople(any(), anyInt(), eq("backend"), anyInt());

        service.explore(null, "   ", null, null, null);
        verify(repository).seedPeople(any(), anyInt(), eq(null), anyInt());
    }

    @Test
    void emptySeed_isAnEmptyGraphWithTheLegendStillFilled() {
        when(repository.seedPeople(any(), anyInt(), any(), anyInt())).thenReturn(List.of());

        GraphResponse response = service.explore(null, null, "ghost", null, null);

        assertThat(response.nodes()).isEmpty();
        assertThat(response.edges()).isEmpty();
        assertThat(response.truncated()).isFalse();
        // The legend describes the company, not the query, so it survives an empty result.
        assertThat(response.totals()).containsEntry(GraphNodeKind.PERSON, 7L);
        verify(repository, never()).subgraph(anyList(), any(), anyInt());
    }

    @Test
    void blankRootId_isTreatedAsNoRoot() {
        service.explore(null, null, "  ", null, null);

        verify(repository).seedPeople(eq(null), anyInt(), any(), anyInt());
    }

    @SuppressWarnings("unchecked")
    private Set<GraphNodeKind> capturedKinds() {
        ArgumentCaptor<Set<GraphNodeKind>> captor = ArgumentCaptor.forClass(Set.class);
        verify(repository).subgraph(anyList(), captor.capture(), anyInt());
        return captor.getValue();
    }
}
