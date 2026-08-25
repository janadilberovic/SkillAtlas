package com.skillatlas.people;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.skillatlas.common.PageResponse;
import com.skillatlas.people.dto.ImportVacaYayRequest;
import com.skillatlas.people.dto.ImportVacaYayResponse;
import com.skillatlas.people.dto.VacaYayRosterRow;

import jakarta.validation.Valid;

/** E3.1 · the import picker and the import itself. Admin only, both of them. */
@RestController
@RequestMapping("/api/v1/people")
public class PeopleImportController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PeopleImportService service;

    public PeopleImportController(PeopleImportService service) {
        this.service = service;
    }

    @GetMapping("/vacayay-roster")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<VacaYayRosterRow> roster(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        return service.roster(PageRequest.of(safePage, safeSize));
    }

    @PostMapping("/import-vacayay")
    @PreAuthorize("hasRole('ADMIN')")
    public ImportVacaYayResponse importFromVacaYay(@Valid @RequestBody ImportVacaYayRequest request) {
        return service.importPeople(request.ids());
    }
}
