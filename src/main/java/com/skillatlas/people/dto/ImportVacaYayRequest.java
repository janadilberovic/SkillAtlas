package com.skillatlas.people.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * The rows the admin ticked, as VacaYAY's own ids and nothing else. Names, emails and roles are
 * deliberately absent: the server re-reads the roster from VacaYAY and takes every field from
 * there, so a hand-written request cannot inject a person — or an ADMIN — that the old system
 * never had.
 */
public record ImportVacaYayRequest(
        @NotEmpty @Size(max = 500) List<Integer> ids) {
}
