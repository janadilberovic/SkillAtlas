package com.skillatlas.graph.dto;

import com.skillatlas.graph.enums.GraphNodeKind;

/**
 * One drawable node, in the shape both the graph explorer (E5.1) and the profile neighbourhood
 * (E4.2) consume.
 *
 * <p>No layout fields: placement is the client force-graph's job, and a server that shipped x/y
 * would have to guess a viewport it cannot see.
 */
public record GraphNode(String id, GraphNodeKind kind, String label, String meta) {
}
