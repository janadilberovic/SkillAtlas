package com.skillatlas.finder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skillatlas.finder.dto.ExpertResponse;
import com.skillatlas.finder.dto.SkillCoverageResponse;
import com.skillatlas.finder.exception.NoSkillsSelectedException;

@Service
public class FinderService {

    /**
     * Lowest KNOWS level that makes someone a go-to person for a skill. Same bar the spec sets for
     * mentor candidates (02·§4.3 uses ≥ 3), raised by one: a mentor teaches, a go-to person is who
     * you page when it breaks.
     */
    static final int EXPERT_LEVEL = 4;

    private final FinderRepository repository;

    public FinderService(FinderRepository repository) {
        this.repository = repository;
    }

    /**
     * Ranked people who know <em>all</em> of {@code skills}, each at or above the minimum level
     * that term asked for. Matching is case-insensitive; ranking is the sum of the matched KNOWS
     * levels. No match is an ordinary empty page, not an error.
     */
    @Transactional(readOnly = true)
    public Page<ExpertResponse> findExperts(List<String> skills, String team, Pageable pageable) {
        List<SkillTerm> terms = parseTerms(skills);
        String teamFilter = normalizeTeam(team);

        List<ExpertResponse> experts = repository.findExperts(
                terms, teamFilter, pageable.getOffset(), pageable.getPageSize());
        // Skips the count query when the first page already holds the whole result.
        return PageableExecutionUtils.getPage(experts, pageable,
                () -> repository.countExperts(terms, teamFilter));
    }

    /**
     * Company-wide coverage of the requested skills, used for the bus-factor readout. Level
     * thresholds in the query are ignored here on purpose: the question is how thin the company is
     * on a skill, not how thin it is on the slice the search happened to ask for.
     */
    @Transactional(readOnly = true)
    public List<SkillCoverageResponse> coverage(List<String> skills) {
        List<String> names = parseTerms(skills).stream().map(SkillTerm::name).toList();
        return repository.skillCoverage(names, EXPERT_LEVEL);
    }

    private List<SkillTerm> parseTerms(List<String> skills) {
        Map<String, Integer> byName = new LinkedHashMap<>();
        if (skills != null) {
            for (String raw : skills) {
                if (raw == null) {
                    continue;
                }
                SkillTerm term = SkillTerm.parse(raw);
                if (term.name().isEmpty()) {
                    continue;
                }
                // The same skill twice ("neo4j + neo4j>=4") — the stricter threshold wins.
                byName.merge(term.name(), term.minLevel(), Math::max);
            }
        }
        if (byName.isEmpty()) {
            throw new NoSkillsSelectedException();
        }
        return byName.entrySet().stream()
                .map(entry -> new SkillTerm(entry.getKey(), entry.getValue()))
                .toList();
    }

    private String normalizeTeam(String team) {
        return team == null || team.isBlank() ? null : team.trim().toLowerCase(Locale.ROOT);
    }
}
