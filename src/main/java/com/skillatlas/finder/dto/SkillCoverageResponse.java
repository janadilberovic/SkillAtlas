package com.skillatlas.finder.dto;

import java.util.List;

// How well one requested skill is covered by the company, ignoring any team filter:
// `knownBy` counts every non-deleted person with a KNOWS edge, `experts` names only those at or
// above the "go-to person" level. An `experts` list of exactly one name is a bus factor of 1.
public record SkillCoverageResponse(String skill, long knownBy, List<String> experts) {
}
