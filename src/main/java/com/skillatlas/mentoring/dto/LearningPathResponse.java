package com.skillatlas.mentoring.dto;

import java.util.List;

import com.skillatlas.graph.dto.GraphEdge;
import com.skillatlas.graph.dto.GraphNode;

/**
 * §4.4: the shortest walk from a person to a skill, drawn with the same nodes and edges the graph
 * explorer uses so the client renders it with the renderer it already has.
 *
 * <p>No path is {@code found = false} with an empty walk, not a 404 — the person and the skill both
 * exist, they are simply not connected yet, and that is an answer the screen has to state.
 *
 * @param ownLevel      the learner's own KNOWS level when they already know the skill, else null
 * @param nearestMentor the first person along the walk who could teach it, if any
 */
public record LearningPathResponse(
        String personId,
        SkillRef skill,
        boolean found,
        int steps,
        Integer ownLevel,
        List<GraphNode> nodes,
        List<GraphEdge> edges,
        NearestMentor nearestMentor) {

    public record NearestMentor(String id, String name, int level) {
    }

    public static LearningPathResponse notFound(String personId, SkillRef skill) {
        return new LearningPathResponse(personId, skill, false, 0, null, List.of(), List.of(), null);
    }
}
