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
 * @param nearestMentor someone who could teach it: the first such person along the walk, or - when
 *                      the walk holds nobody, which is what a one-hop "you already know it" path
 *                      looks like - the strongest one in the company
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

    /** @param onPath false when the walk offered nobody and this is a company-wide fallback */
    public record NearestMentor(String id, String name, int level, boolean onPath) {
    }

    /** The walk to a mentor is found without reading the learner's own level; this fills it in. */
    public LearningPathResponse withOwnLevel(Integer level) {
        return new LearningPathResponse(personId, skill, found, steps, level, nodes, edges, nearestMentor);
    }

    public static LearningPathResponse notFound(String personId, SkillRef skill) {
        return new LearningPathResponse(personId, skill, false, 0, null, List.of(), List.of(), null);
    }
}
