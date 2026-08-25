package com.skillatlas.people;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.skillatlas.common.PageResponse;
import com.skillatlas.people.PeopleImportRepository.MergedRow;
import com.skillatlas.people.dto.ImportVacaYayResponse;
import com.skillatlas.people.dto.PersonResponse;
import com.skillatlas.people.dto.VacaYayRosterRow;
import com.skillatlas.people.enums.Role;
import com.skillatlas.vacayay.VacaYayClient;
import com.skillatlas.vacayay.dto.VacaYayEmployee;

import jakarta.validation.Validator;

/**
 * E3 · the old system's employees become {@code Person} nodes. Idempotent by email: a second press
 * of Import can only ever produce skips.
 */
@Service
public class PeopleImportService {

    private final VacaYayClient vacaYay;
    private final PeopleImportRepository repository;
    private final Validator validator;

    public PeopleImportService(VacaYayClient vacaYay, PeopleImportRepository repository,
            Validator validator) {
        this.vacaYay = vacaYay;
        this.repository = repository;
        this.validator = validator;
    }

    /** The picker's page. Paged in memory because VacaYAY hands over the whole roster at once. */
    @Transactional(readOnly = true)
    public PageResponse<VacaYayRosterRow> roster(Pageable pageable) {
        List<VacaYayRosterRow> rows = rosterRows(vacaYay.fetchEmployees());
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = Math.min(from + pageable.getPageSize(), rows.size());
        return PageResponse.from(new PageImpl<>(rows.subList(from, to), pageable, rows.size()));
    }

    /**
     * Every field written to the graph comes from this method's own fetch, never from the request —
     * the request carries ids and nothing else, so it cannot smuggle in a person, an email or a
     * role the old system never had.
     */
    @Transactional
    public ImportVacaYayResponse importPeople(List<Integer> ids) {
        List<VacaYayEmployee> employees = vacaYay.fetchEmployees();
        Map<Integer, VacaYayEmployee> byId = new LinkedHashMap<>();
        employees.forEach(e -> byId.putIfAbsent(e.id(), e));
        Set<String> taken = repository.takenEmails(normalisedEmails(employees));

        int skipped = 0;
        int invalid = 0;
        int notFound = 0;
        Set<String> batch = new HashSet<>();
        List<Map<String, Object>> rows = new ArrayList<>();

        for (Integer id : new LinkedHashSet<>(ids)) {
            VacaYayEmployee employee = byId.get(id);
            if (employee == null) {
                notFound++;
                continue;
            }
            if (!validator.validate(employee).isEmpty()) {
                invalid++;
                continue;
            }
            String email = normalise(employee.email());
            // Already in the graph, or the same address twice in one batch.
            if (taken.contains(email) || !batch.add(email)) {
                skipped++;
                continue;
            }
            rows.add(row(employee, email));
        }

        List<MergedRow> merged = rows.isEmpty() ? List.of()
                : repository.mergePeople(rows, ZonedDateTime.now(ZoneOffset.UTC));
        List<PersonResponse> people = new ArrayList<>();
        for (MergedRow row : merged) {
            if (row.created()) {
                people.add(toResponse(row));
            } else {
                // The email was claimed between the probe and the MERGE. Rare, but the MERGE is
                // what decides — which is the reason it is a MERGE and not a CREATE.
                skipped++;
            }
        }
        return new ImportVacaYayResponse(people.size(), skipped, invalid, notFound, people);
    }

    private List<VacaYayRosterRow> rosterRows(List<VacaYayEmployee> employees) {
        Set<String> taken = repository.takenEmails(normalisedEmails(employees));
        return employees.stream()
                .map(e -> new VacaYayRosterRow(e.id(), e.firstName(), e.lastName(), e.email(),
                        e.jobTitle(), e.isActive(),
                        taken.contains(normalise(e.email())), issue(e)))
                .sorted(Comparator.comparing(VacaYayRosterRow::lastName, caseInsensitiveNullsLast())
                        .thenComparing(VacaYayRosterRow::firstName, caseInsensitiveNullsLast()))
                .toList();
    }

    /** The old system is not a trusted source; a malformed row is shown as blocked, never imported. */
    private String issue(VacaYayEmployee employee) {
        return validator.validate(employee).stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> row(VacaYayEmployee employee, String email) {
        // HashMap, not Map.of: jobTitle is nullable on the VacaYAY side.
        Map<String, Object> row = new HashMap<>();
        row.put("newId", UUID.randomUUID().toString());
        row.put("email", email);
        row.put("firstName", employee.firstName().trim());
        row.put("lastName", employee.lastName().trim());
        row.put("position", trimmedOrNull(employee.jobTitle()));
        row.put("active", employee.isActive());
        return row;
    }

    // A just-imported person has no teams and no skills — which is exactly what puts them in the
    // dashboard's "waiting for skill mapping" queue (E3.1).
    private PersonResponse toResponse(MergedRow row) {
        return PersonResponse.of(row.id(), row.email(), row.firstName(), row.lastName(),
                row.position(), Role.MEMBER, row.active(), null, List.of(), List.of());
    }

    private List<String> normalisedEmails(List<VacaYayEmployee> employees) {
        return employees.stream()
                .map(e -> normalise(e.email()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static String normalise(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase(Locale.ROOT) : null;
    }

    private static String trimmedOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static Comparator<String> caseInsensitiveNullsLast() {
        return Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
    }
}
