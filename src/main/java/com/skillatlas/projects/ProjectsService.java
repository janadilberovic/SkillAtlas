package com.skillatlas.projects;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.skillatlas.people.PeopleRepository;
import com.skillatlas.people.exception.PersonNotFoundException;
import com.skillatlas.projects.domain.Project;
import com.skillatlas.projects.dto.ProjectCreateRequest;
import com.skillatlas.projects.dto.ProjectDetailResponse;
import com.skillatlas.projects.dto.ProjectMemberRequest;
import com.skillatlas.projects.dto.ProjectResponse;
import com.skillatlas.projects.dto.ProjectUpdateRequest;
import com.skillatlas.projects.exception.ProjectNotFoundException;
import com.skillatlas.skills.SkillsRepository;
import com.skillatlas.skills.domain.Skill;
import com.skillatlas.skills.exception.SkillNotFoundException;

@Service
public class ProjectsService {

    private final ProjectsRepository repository;
    private final ProjectMembersRepository membersRepository;
    private final SkillsRepository skillsRepository;
    private final PeopleRepository peopleRepository;

    public ProjectsService(ProjectsRepository repository,
            ProjectMembersRepository membersRepository,
            SkillsRepository skillsRepository,
            PeopleRepository peopleRepository) {
        this.repository = repository;
        this.membersRepository = membersRepository;
        this.skillsRepository = skillsRepository;
        this.peopleRepository = peopleRepository;
    }

    @Transactional(readOnly = true)
    public Project getById(String id) {
        return repository.findById(id).orElseThrow(() -> new ProjectNotFoundException(id));
    }

    /** The project plus its WORKED_ON roster — two queries, never one per member. */
    @Transactional(readOnly = true)
    public ProjectDetailResponse detail(String id) {
        Project project = getById(id);
        return ProjectDetailResponse.from(project, membersRepository.findMembers(id));
    }

    /** One page of cards. Head counts come back for the whole page in a single query. */
    @Transactional(readOnly = true)
    public Page<ProjectResponse> list(String search, Pageable pageable) {
        Page<Project> page = StringUtils.hasText(search)
                ? repository.findByNameContainingIgnoreCase(search.trim(), pageable)
                : repository.findAll(pageable);
        List<String> ids = page.getContent().stream().map(Project::getId).toList();
        Map<String, Integer> counts = membersRepository.countByProject(ids);
        return page.map(p -> ProjectResponse.from(p, counts.getOrDefault(p.getId(), 0)));
    }

    @Transactional
    public Project create(ProjectCreateRequest request) {
        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setActive(request.active() == null || request.active());
        project.setUses(resolveSkills(request.skillIds()));
        return repository.save(project);
    }

    @Transactional
    public Project update(String id, ProjectUpdateRequest request) {
        Project project = getById(id);
        project.setName(request.name());
        project.setDescription(request.description());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setActive(request.active());
        project.setUses(resolveSkills(request.skillIds()));
        return repository.save(project);
    }

    @Transactional
    public void delete(String id) {
        Project project = getById(id);
        repository.delete(project);
    }

    @Transactional
    public void assignMember(String projectId, String personId, ProjectMemberRequest request) {
        getById(projectId); // 404 if the project is missing
        if (peopleRepository.findByIdAndDeletedFalse(personId).isEmpty()) {
            throw new PersonNotFoundException(personId);
        }
        repository.assignMember(projectId, personId, request.role(), request.from(), request.to());
    }

    @Transactional
    public void removeMember(String projectId, String personId) {
        getById(projectId); // 404 if the project is missing
        repository.removeMember(projectId, personId);
    }

    // Resolve skill ids to managed Skill nodes for the USES relationship; unknown id -> 404.
    private Set<Skill> resolveSkills(Set<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<String> requested = new LinkedHashSet<>(skillIds);
        Set<Skill> found = new LinkedHashSet<>();
        skillsRepository.findAllById(requested).forEach(found::add);
        if (found.size() != requested.size()) {
            Set<String> foundIds = new HashSet<>();
            found.forEach(s -> foundIds.add(s.getId()));
            String missing = requested.stream()
                    .filter(id -> !foundIds.contains(id))
                    .findFirst()
                    .orElseThrow();
            throw new SkillNotFoundException(missing);
        }
        return found;
    }
}
