package com.skillatlas.dashboard;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skillatlas.dashboard.dto.DashboardResponse;

/**
 * E6.3. Admin-only: the gap and bus-factor widgets are a readout of where the company is thin,
 * naming the single person a skill depends on.
 *
 * <p>No paging — every widget is capped in Cypher, and a dashboard that paginates is a report.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public DashboardResponse overview() {
        return service.overview();
    }
}
