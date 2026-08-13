package com.skillatlas.graph.dto;

/** {@code source} and {@code target} are {@link GraphNode#id()} values, not node indices. */
public record GraphEdge(String source, String target, String type) {
}
