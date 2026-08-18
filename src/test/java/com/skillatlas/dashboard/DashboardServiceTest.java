package com.skillatlas.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.skillatlas.dashboard.dto.DashboardResponse;
import com.skillatlas.mentoring.MentoringService;

// The dashboard's own logic is the thresholds and caps it hands the repository; the widgets
// themselves are Cypher and are covered by DashboardIT.
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    DashboardRepository repository;
    @InjectMocks
    DashboardService service;

    @Test
    void gapThresholdAndCaps_comeFromTheServerNotTheCaller() {
        when(repository.metrics()).thenReturn(new DashboardResponse.Metrics(1, 2, 3, 4));
        when(repository.skillGap(anyInt(), anyLong(), anyInt())).thenReturn(List.of());
        when(repository.mentorRequests(anyInt(), anyLong(), anyInt())).thenReturn(List.of());
        when(repository.busFactor(anyInt())).thenReturn(List.of());
        when(repository.mappingQueue(anyInt()))
                .thenReturn(new DashboardResponse.MappingQueue(0, List.of()));

        service.overview();

        verify(repository).skillGap(DashboardService.GAP_THRESHOLD, 0L, DashboardService.GAP_PAGE_SIZE);
        // The level bar on a request row has to match what the matching endpoint will offer.
        verify(repository).mentorRequests(
                MentoringService.MIN_MENTOR_LEVEL, 0L, DashboardService.REQUEST_PAGE_SIZE);
        verify(repository).busFactor(DashboardService.ROW_LIMIT);
        verify(repository).mappingQueue(DashboardService.QUEUE_PREVIEW);
    }

    @Test
    void aShortFirstPage_doesNotPayForACountQuery() {
        // One row on a ten-row page is the whole answer; counting it again would be a wasted trip.
        when(repository.skillGap(anyInt(), anyLong(), anyInt())).thenReturn(
                List.of(new DashboardResponse.SkillGapRow("Backend", "Neo4j", List.of("Atlas"), 1)));

        var page = service.skillGap(PageRequest.of(0, 10));

        assertThat(page.totalElements()).isEqualTo(1);
        verify(repository, never()).countSkillGap(anyInt());
    }

    @Test
    void aFullPage_asksHowManyThereAreInTotal() {
        when(repository.skillGap(anyInt(), anyLong(), anyInt())).thenReturn(
                List.of(new DashboardResponse.SkillGapRow("Backend", "Neo4j", List.of("Atlas"), 1)));
        when(repository.countSkillGap(DashboardService.GAP_THRESHOLD)).thenReturn(37L);

        var page = service.skillGap(PageRequest.of(0, 1));

        assertThat(page.totalElements()).isEqualTo(37);
        assertThat(page.totalPages()).isEqualTo(37);
    }

    @Test
    void overview_carriesEveryWidgetInOneAnswer() {
        when(repository.metrics()).thenReturn(new DashboardResponse.Metrics(40, 30, 4, 6));
        when(repository.skillGap(anyInt(), anyLong(), anyInt())).thenReturn(
                List.of(new DashboardResponse.SkillGapRow("Backend", "Neo4j", List.of("Atlas"), 1)));
        when(repository.mentorRequests(anyInt(), anyLong(), anyInt())).thenReturn(List.of(
                new DashboardResponse.MentorRequestRow("p3", "Cara Cache", "s1", "Neo4j", null, 4)));
        when(repository.busFactor(anyInt())).thenReturn(
                List.of(new DashboardResponse.BusFactorRow("Neo4j", "p1", "Ada Lovelace")));
        when(repository.mappingQueue(anyInt())).thenReturn(new DashboardResponse.MappingQueue(
                12, List.of(new DashboardResponse.PersonRef("p2", "Bob Byte"))));

        DashboardResponse overview = service.overview();

        assertThat(overview.metrics().people()).isEqualTo(40);
        assertThat(overview.skillGap().content()).hasSize(1);
        assertThat(overview.mentorRequests().content()).hasSize(1);
        assertThat(overview.mentorRequests().content().get(0).candidates()).isEqualTo(4);
        assertThat(overview.busFactor()).hasSize(1);
        // The preview is capped but the count is not — the screen says "12 waiting", not "1".
        assertThat(overview.mappingQueue().total()).isEqualTo(12);
        assertThat(overview.mappingQueue().people()).hasSize(1);
    }
}
