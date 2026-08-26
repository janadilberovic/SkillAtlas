package com.skillatlas.people.dto;

import java.util.List;

/**
 * What one press of Import did. The four counters add up to the number of ids that were sent, so
 * the screen can explain a run that imported fewer people than were ticked.
 *
 * @param imported people created by this run
 * @param skipped  the email already belonged to someone (active or soft-deleted)
 * @param invalid  the old system's row failed validation — a blank name, a malformed email
 * @param notFound an id VacaYAY no longer returns
 * @param people   the freshly created people; they have no teams and no skills yet, by definition
 */
public record ImportVacaYayResponse(
        int imported,
        int skipped,
        int invalid,
        int notFound,
        List<PersonResponse> people) {
}
