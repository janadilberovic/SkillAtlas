package com.skillatlas.dashboard;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skillatlas.dashboard.dto.DashboardResponse;

@Service
public class DashboardService {

    /** "0 or 1 person knows it" is the spec's definition of a gap. */
    static final int GAP_THRESHOLD = 1;
    static final int ROW_LIMIT = 50;
    static final int QUEUE_PREVIEW = 12;

    private final DashboardRepository repository;

    public DashboardService(DashboardRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse overview() {
        return new DashboardResponse(
                repository.metrics(),
                repository.skillGap(GAP_THRESHOLD, ROW_LIMIT),
                repository.busFactor(ROW_LIMIT),
                repository.mappingQueue(QUEUE_PREVIEW));
    }
}
