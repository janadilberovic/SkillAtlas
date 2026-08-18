package com.skillatlas.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.skillatlas.dashboard.dto.DashboardResponse;

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
        when(repository.skillGap(anyInt(), anyInt())).thenReturn(List.of());
        when(repository.busFactor(anyInt())).thenReturn(List.of());
        when(repository.mappingQueue(anyInt()))
                .thenReturn(new DashboardResponse.MappingQueue(0, List.of()));

        service.overview();

        verify(repository).skillGap(DashboardService.GAP_THRESHOLD, DashboardService.ROW_LIMIT);
        verify(repository).busFactor(DashboardService.ROW_LIMIT);
        verify(repository).mappingQueue(DashboardService.QUEUE_PREVIEW);
    }

    @Test
    void overview_carriesEveryWidgetInOneAnswer() {
        when(repository.metrics()).thenReturn(new DashboardResponse.Metrics(40, 30, 4, 6));
        when(repository.skillGap(anyInt(), anyInt())).thenReturn(
                List.of(new DashboardResponse.SkillGapRow("Backend", "Neo4j", List.of("Atlas"), 1)));
        when(repository.busFactor(anyInt())).thenReturn(
                List.of(new DashboardResponse.BusFactorRow("Neo4j", "p1", "Ada Lovelace")));
        when(repository.mappingQueue(anyInt())).thenReturn(new DashboardResponse.MappingQueue(
                12, List.of(new DashboardResponse.PersonRef("p2", "Bob Byte"))));

        DashboardResponse overview = service.overview();

        assertThat(overview.metrics().people()).isEqualTo(40);
        assertThat(overview.skillGap()).hasSize(1);
        assertThat(overview.busFactor()).hasSize(1);
        // The preview is capped but the count is not — the screen says "12 waiting", not "1".
        assertThat(overview.mappingQueue().total()).isEqualTo(12);
        assertThat(overview.mappingQueue().people()).hasSize(1);
    }
}
