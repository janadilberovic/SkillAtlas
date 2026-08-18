package com.skillatlas.graph.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.skillatlas.graph.exception.InvalidNodeTypeException;

/**
 * The node kinds the explorer can draw, and the whitelist behind the {@code types} filter.
 *
 * <p>Parsing through an enum is also the injection defence for {@code types}: the parameter never
 * reaches Cypher as text, only as the booleans that decide which subquery runs.
 */
public enum GraphNodeKind {
    PERSON,
    SKILL,
    PROJECT,
    TEAM;

    /** Shared between every caller that asks for the default view, so it must not be mutable. */
    private static final Set<GraphNodeKind> ALL =
            Collections.unmodifiableSet(EnumSet.allOf(GraphNodeKind.class));

    /** Empty or absent means "everything" — the explorer's default view is the whole map. */
    public static Set<GraphNodeKind> parse(List<String> raw) {
        if (raw == null) {
            return ALL;
        }
        Set<GraphNodeKind> kinds = EnumSet.noneOf(GraphNodeKind.class);
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                // Locale.ROOT, not the default locale: on a Turkish one "skill" upper-cases to
                // "SKİLL" and no constant matches, turning a valid request into a 400.
                kinds.add(valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                throw new InvalidNodeTypeException(value.trim());
            }
        }
        return kinds.isEmpty() ? ALL : kinds;
    }
}
