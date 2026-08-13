package com.skillatlas.graph.enums;

import java.util.LinkedHashSet;
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

    /** Empty or absent means "everything" — the explorer's default view is the whole map. */
    public static Set<GraphNodeKind> parse(List<String> raw) {
        if (raw == null) {
            return Set.of(values());
        }
        Set<GraphNodeKind> kinds = new LinkedHashSet<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                kinds.add(valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                throw new InvalidNodeTypeException(value.trim());
            }
        }
        return kinds.isEmpty() ? Set.of(values()) : kinds;
    }
}
