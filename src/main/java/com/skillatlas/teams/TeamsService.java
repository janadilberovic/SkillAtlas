package com.skillatlas.teams;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skillatlas.teams.domain.Team;
import com.skillatlas.teams.dto.TeamCreateRequest;
import com.skillatlas.teams.dto.TeamUpdateRequest;
import com.skillatlas.teams.exception.TeamNameAlreadyExistsException;
import com.skillatlas.teams.exception.TeamNotFoundException;

@Service
public class TeamsService {

    private final TeamsRepository repository;

    public TeamsService(TeamsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Team getById(String id) {
        return repository.findById(id).orElseThrow(() -> new TeamNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<Team> list(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional
    public Team create(TeamCreateRequest request) {
        if (repository.existsByName(request.name())) {
            throw new TeamNameAlreadyExistsException(request.name());
        }
        Team team = new Team();
        team.setName(request.name());
        return repository.save(team);
    }

    @Transactional
    public Team update(String id, TeamUpdateRequest request) {
        Team team = getById(id);
        if (!team.getName().equals(request.name()) && repository.existsByName(request.name())) {
            throw new TeamNameAlreadyExistsException(request.name());
        }
        team.setName(request.name());
        return repository.save(team);
    }

    @Transactional
    public void delete(String id) {
        Team team = getById(id);
        repository.delete(team);
    }
}
