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
import com.skillatlas.finder.exception.NoSkillsSelectedException;

// Unit tests for the finder's query normalization and paging, with a mocked repository (no database).
@ExtendWith(MockitoExtension.class)
class FinderServiceTest {

    @Mock
    FinderRepository repository;
    @InjectMocks
    FinderService service;

    @SuppressWarnings("unchecked")
    private Collection<String> capturedSkillNames() {
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(repository).findExperts(captor.capture(), any(), anyLong(), anyInt());
        return captor.getValue();
    }

    @Test
    void skillTerms_areTrimmedLowercasedAndDeduplicated() {
        when(repository.findExperts(any(), any(), anyLong(), anyInt())).thenReturn(List.of());

        service.findExperts(List.of("  Neo4j ", "DOCKER", "neo4j"), null, PageRequest.of(0, 20));

        assertThat(capturedSkillNames()).containsExactly("neo4j", "docker");
    }

    @Test
    void blankAndNullTerms_areDropped() {
        when(repository.findExperts(any(), any(), anyLong(), anyInt())).thenReturn(List.of());

        service.findExperts(Arrays.asList("Neo4j", "   ", null, ""), null, PageRequest.of(0, 20));

        assertThat(capturedSkillNames()).containsExactly("neo4j");
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
                new ExpertResponse("p1", "ada@test.com", "Ada", "Lovelace", "Engineer", 8,
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
}
