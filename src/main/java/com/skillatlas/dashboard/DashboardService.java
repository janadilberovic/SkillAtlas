package com.skillatlas.dashboard;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skillatlas.common.PageResponse;
import com.skillatlas.dashboard.dto.DashboardResponse;
import com.skillatlas.mentoring.MentoringService;

@Service
public class DashboardService {

    /** "0 or 1 person knows it" is the spec's definition of a gap. */
    static final int GAP_THRESHOLD = 1;
    /** What the overview embeds: enough to see the shape, few enough to read. */
    static final int GAP_PAGE_SIZE = 10;
    static final int REQUEST_PAGE_SIZE = 10;
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
                skillGap(PageRequest.of(0, GAP_PAGE_SIZE)),
                mentorRequests(PageRequest.of(0, REQUEST_PAGE_SIZE)),
                repository.busFactor(ROW_LIMIT),
                repository.mappingQueue(QUEUE_PREVIEW));
    }

    /**
     * The gap table pages on its own so the screen can walk a long list without re-running the
     * other three widgets — on a real headcount this list is the only one that grows without bound.
     */
    @Transactional(readOnly = true)
    public PageResponse<DashboardResponse.SkillGapRow> skillGap(Pageable pageable) {
        List<DashboardResponse.SkillGapRow> rows = repository.skillGap(
                GAP_THRESHOLD, pageable.getOffset(), pageable.getPageSize());
        // Skips the count query when the first page already holds the whole result.
        return PageResponse.from(PageableExecutionUtils.getPage(rows, pageable,
                () -> repository.countSkillGap(GAP_THRESHOLD)));
    }

    /**
     * Wishes waiting for a mentor. The level bar comes from {@link MentoringService} rather than a
     * copy: the count on a row promises what the matching endpoint will actually offer, and two
     * constants would eventually disagree.
     */
    @Transactional(readOnly = true)
    public PageResponse<DashboardResponse.MentorRequestRow> mentorRequests(Pageable pageable) {
        List<DashboardResponse.MentorRequestRow> rows = repository.mentorRequests(
                MentoringService.MIN_MENTOR_LEVEL, pageable.getOffset(), pageable.getPageSize());
        return PageResponse.from(PageableExecutionUtils.getPage(rows, pageable,
                repository::countMentorRequests));
    }
}
