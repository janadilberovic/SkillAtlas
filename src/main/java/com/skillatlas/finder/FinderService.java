package com.skillatlas.finder;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skillatlas.finder.dto.ExpertResponse;
import com.skillatlas.finder.exception.NoSkillsSelectedException;

@Service
public class FinderService {

    private final FinderRepository repository;

    public FinderService(FinderRepository repository) {
        this.repository = repository;
    }

    /**
     * Ranked people who know <em>all</em> of {@code skills}. Matching is case-insensitive; ranking
     * is the sum of the matched KNOWS levels. No match is an ordinary empty page, not an error.
     */
    @Transactional(readOnly = true)
    public Page<ExpertResponse> findExperts(List<String> skills, String team, Pageable pageable) {
        List<String> skillNames = normalizeSkills(skills);
        if (skillNames.isEmpty()) {
            throw new NoSkillsSelectedException();
        }
        String teamFilter = normalizeTeam(team);

        List<ExpertResponse> experts = repository.findExperts(
                skillNames, teamFilter, pageable.getOffset(), pageable.getPageSize());
        // Skips the count query when the first page already holds the whole result.
        return PageableExecutionUtils.getPage(experts, pageable,
                () -> repository.countExperts(skillNames, teamFilter));
    }

    private List<String> normalizeSkills(List<String> skills) {
        if (skills == null) {
            return List.of();
        }
        return skills.stream()
                .filter(Objects::nonNull)
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private String normalizeTeam(String team) {
        return team == null || team.isBlank() ? null : team.trim().toLowerCase(Locale.ROOT);
    }
}
