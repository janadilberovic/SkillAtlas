package com.skillatlas.people.dto;

/**
 * One row of the import picker (E3.1). The two trailing flags are why the roster exists at all:
 * they let the screen disable a checkbox and say why, instead of letting the admin tick someone
 * the import would only ever skip.
 *
 * @param alreadyImported the email is already taken in the graph — including by a soft-deleted
 *                        person, because the unique constraint on {@code Person.email} ignores the
 *                        flag too
 * @param issue           why this row cannot be imported, or {@code null} when it can
 */
public record VacaYayRosterRow(
        int id,
        String firstName,
        String lastName,
        String email,
        String position,
        boolean active,
        boolean alreadyImported,
        String issue) {
}
