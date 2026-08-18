package com.skillatlas.graph.dto;

import java.util.List;
import java.util.Map;

import com.skillatlas.graph.enums.GraphNodeKind;

/**
 * A capped slice of the company graph.
 *
 * <p>{@code totalRelations} is the number of relationships found before the cap, so the client can
 * say "showing 150 of 1 204" honestly; {@code truncated} is that comparison already made.
 * {@code totals} is company-wide per kind and ignores every filter — it feeds the legend, which
 * answers "how big is the whole map", not "what is on screen".
 */
public record GraphResponse(
        List<GraphNode> nodes,
        List<GraphEdge> edges,
        int totalRelations,
        Map<GraphNodeKind, Long> totals,
        boolean truncated) {

    public GraphResponse withTotals(Map<GraphNodeKind, Long> value) {
        return new GraphResponse(nodes, edges, totalRelations, value, truncated);
    }
}
