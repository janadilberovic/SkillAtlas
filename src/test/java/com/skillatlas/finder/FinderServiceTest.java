package com.skillatlas.finder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.skillatlas.finder.dto.ExpertResponse;
import com.skillatlas.finder.exception.InvalidSkillLevelException;
import com.skillatlas.finder.exception.NoSkillsSelectedException;

// Unit tests for the finder's query normalization and paging, with a mocked repository (no database).
@ExtendWith(MockitoExtension.class)
class FinderServiceTest {

    @Mock
    FinderRepository repository;
    @InjectMocks
    FinderService service;

    @SuppressWarnings("unchecked")
    private Collection<SkillTerm> capturedTerms() {
        ArgumentCaptor<Collection<SkillTerm>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(repository).findExperts(captor.capture(), any(), anyLong(), anyInt());
        return captor.getValue();
    }

    @Test
    void skillTerms_areTrimmedLowercasedAndDeduplicated() {
        when(repository.findExperts(any(), any(), anyLong(), anyInt())).thenReturn(List.of());

        service.findExperts(List.of("  Neo4j ", "DOCKER", "neo4j"), null, PageRequest.of(0, 20));

        assertThat(capturedTerms()).containsExactly(
                new SkillTerm("neo4j", 1), new SkillTerm("docker", 1));
    }

    @Test
    void blankAndNullTerms_areDropped() {
        when(repository.findExperts(any(), any(), anyLong(), anyInt())).thenReturn(List.of());

        service.findExperts(Arrays.asList("Neo4j", "   ", null, ""), null, PageRequest.of(0, 20));

        assertThat(capturedTerms()).containsExactly(new SkillTerm("neo4j", 1));
    }

    @Test
    void noUsableSkill_isRejectedBeforeTouchingTheDatabase() {
        assertThatThrownBy(() -> service.findExperts(List.of("  ", ""), null, PageRequest.of(0, 20)))
                .isInstanceOf(NoSkillsSelectedException.class);
        assertThatThrownBy(() -> service.findExperts(null, null, PageRequest.of(0, 20)))
                .isInstanceOf(NoSkillsSelectedException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void levelThreshold_isParsedPerSkill() {
        when(repository.findExperts(any(), any(), anyLong(), anyInt())).thenReturn(List.of());

        service.findExperts(List.of("Neo4j >= 4", "React", "Docker ≥ 2"), null, PageRequest.of(0, 20));

        assertThat(capturedTerms()).containsExactly(
                new SkillTerm("neo4j", 4), new SkillTerm("react", 1), new SkillTerm("docker", 2));
    }

    @Test
    void strictGreaterThan_isOneLevelHigherThanWritten() {
        when(repository.findExperts(any(), any(), anyLong(), anyInt())).thenReturn(List.of());

        service.findExperts(List.of("Neo4j > 3"), null, PageRequest.of(0, 20));

        assertThat(capturedTerms()).containsExactly(new SkillTerm("neo4j", 4));
    }

    @Test
    void sameSkillTwice_keepsTheStricterThreshold() {
        when(repository.findExperts(any(), any(), anyLong(), anyInt())).thenReturn(List.of());

        service.findExperts(List.of("neo4j", "Neo4j>=5", "NEO4J >= 2"), null, PageRequest.of(0, 20));

        assertThat(capturedTerms()).containsExactly(new SkillTerm("neo4j", 5));
    }

    @Test
    void levelOutsideOneToFive_isRejected() {
        assertThatThrownBy(() -> service.findExperts(List.of("neo4j >= 0"), null, PageRequest.of(0, 20)))
                .isInstanceOf(InvalidSkillLevelException.class);
        assertThatThrownBy(() -> service.findExperts(List.of("neo4j >= 6"), null, PageRequest.of(0, 20)))
                .isInstanceOf(InvalidSkillLevelException.class);
        // "> 5" would mean level 6, which no KNOWS edge can ever have.
        assertThatThrownBy(() -> service.findExperts(List.of("neo4j > 5"), null, PageRequest.of(0, 20)))
                .isInstanceOf(InvalidSkillLevelException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void injectionPayload_staysOneOrdinarySkillName() {
        when(repository.findExperts(any(), any(), anyLong(), anyInt())).thenReturn(List.of());

        service.findExperts(List.of("React'}) DETACH DELETE (n) //"), null, PageRequest.of(0, 20));

        assertThat(capturedTerms())
                .containsExactly(new SkillTerm("react'}) detach delete (n) //", 1));
    }

    @Test
    void blankTeam_meansNoTeamFilter() {
        when(repository.findExperts(any(), any(), anyLong(), anyInt())).thenReturn(List.of());

        service.findExperts(List.of("neo4j"), "  ", PageRequest.of(0, 20));

        verify(repository).findExperts(any(), isNull(), anyLong(), anyInt());
    }

    @Test
    void teamFilter_isLowercasedForCaseInsensitiveMatching() {
        when(repository.findExperts(any(), any(), anyLong(), anyInt())).thenReturn(List.of());

        service.findExperts(List.of("neo4j"), " Backend ", PageRequest.of(0, 20));

        verify(repository).findExperts(any(), eq("backend"), anyLong(), anyInt());
    }

    @Test
    void noMatch_isAnEmptyPageNotAnError() {
        when(repository.findExperts(any(), any(), anyLong(), anyInt())).thenReturn(List.of());

        Page<ExpertResponse> page = service.findExperts(List.of("cobol"), null, PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void shortFirstPage_skipsTheCountQuery() {
        when(repository.findExperts(any(), any(), anyLong(), anyInt())).thenReturn(List.of(
                new ExpertResponse("p1", "ada@test.com", "Ada", "Lovelace", "Engineer",
                        List.of("Backend"), 8,
                        List.of(new ExpertResponse.MatchedSkill("Neo4j", 5)))));

        Page<ExpertResponse> page = service.findExperts(List.of("neo4j"), null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        verify(repository, never()).countExperts(any(), any());
    }

    @Test
    void pageRequest_isTranslatedToSkipAndLimit() {
        when(repository.findExperts(any(), any(), anyLong(), anyInt())).thenReturn(List.of());

        service.findExperts(List.of("neo4j"), null, PageRequest.of(2, 10));

        verify(repository).findExperts(any(), isNull(), eq(20L), eq(10));
    }

    @Test
    void coverage_ignoresLevelThresholdsAndAsksForTheExpertBar() {
        when(repository.skillCoverage(any(), anyInt())).thenReturn(List.of());

        service.coverage(List.of("Neo4j >= 5", "Docker"));

        verify(repository).skillCoverage(List.of("neo4j", "docker"), FinderService.EXPERT_LEVEL);
    }
}
