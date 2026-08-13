package com.skillatlas.graph;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skillatlas.graph.dto.GraphResponse;
import com.skillatlas.graph.enums.GraphNodeKind;

@Service
public class GraphService {

    static final int DEFAULT_LIMIT = 150;
    /** A force layout stops being readable long before this; the browser gives up sooner still. */
    static final int MAX_LIMIT = 500;
    static final int MIN_HOPS = 1;
    static final int MAX_HOPS = 2;

    private final GraphRepository repository;

    public GraphService(GraphRepository repository) {
        this.repository = repository;
    }

    /**
     * A capped slice of the graph. Without {@code rootId} it samples the company (optionally one
     * team); with it, the neighbourhood around one person.
     *
     * <p>An unknown {@code types} value is a 400, but an empty result is an ordinary empty graph —
     * a missing or soft-deleted root produces no seed, and so no nodes, rather than a 404. The
     * explorer is a view of what exists, not a lookup of one resource.
     */
    @Transactional(readOnly = true)
    public GraphResponse explore(List<String> types, String team, String rootId, Integer hops,
            Integer limit) {
        Set<GraphNodeKind> kinds = GraphNodeKind.parse(types);
        int safeLimit = clamp(limit == null ? DEFAULT_LIMIT : limit, 1, MAX_LIMIT);
        int safeHops = clamp(hops == null ? MAX_HOPS : hops, MIN_HOPS, MAX_HOPS);

        // The seed cap rides on the relationship cap: more seed people than the edges we are
        // willing to return can only produce work the slice throws away.
        List<String> seed = repository.seedPeople(
                blankToNull(rootId), safeHops, normalizeTeam(team), safeLimit);
        if (seed.isEmpty()) {
            return new GraphResponse(List.of(), List.of(), 0, repository.totals(), false);
        }
        return repository.subgraph(seed, kinds, safeLimit).withTotals(repository.totals());
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private static String normalizeTeam(String team) {
        return team == null || team.isBlank() ? null : team.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
