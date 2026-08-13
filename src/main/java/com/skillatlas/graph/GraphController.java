package com.skillatlas.graph;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.skillatlas.graph.dto.GraphResponse;

/**
 * E5.1 graph explorer: a bounded slice of the company map.
 *
 * <p>Readable by any authenticated user (spec 02·§3 grants graph read to members), so no role
 * guard here — SecurityConfig already requires a token for everything outside /auth.
 *
 * <p>{@code rootId} and {@code hops} are what the profile's "in the graph" jump needs; without them
 * the endpoint answers the spec's plain {@code types}/{@code team}/{@code limit} form.
 */
@RestController
@RequestMapping("/api/v1/graph")
public class GraphController {

    private final GraphService service;

    public GraphController(GraphService service) {
        this.service = service;
    }

    @GetMapping
    public GraphResponse explore(
            @RequestParam(required = false) List<String> types,
            @RequestParam(required = false) String team,
            @RequestParam(required = false) String rootId,
            @RequestParam(required = false) Integer hops,
            @RequestParam(required = false) Integer limit) {
        return service.explore(types, team, rootId, hops, limit);
    }
}
