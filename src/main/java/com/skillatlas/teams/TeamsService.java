package com.skillatlas.teams;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skillatlas.people.PeopleRepository;
import com.skillatlas.people.exception.PersonNotFoundException;
import com.skillatlas.teams.domain.Team;
import com.skillatlas.teams.dto.TeamCreateRequest;
import com.skillatlas.teams.dto.TeamUpdateRequest;
import com.skillatlas.teams.exception.TeamNameAlreadyExistsException;
import com.skillatlas.teams.exception.TeamNotFoundException;

@Service
public class TeamsService {

    private final TeamsRepository repository;
    private final PeopleRepository peopleRepository;

    public TeamsService(TeamsRepository repository, PeopleRepository peopleRepository) {
        this.repository = repository;
        this.peopleRepository = peopleRepository;
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

    /** Puts a person in a team (MEMBER_OF). Idempotent — repeating it adds no second edge. */
    @Transactional
    public void addMember(String teamId, String personId) {
        getById(teamId); // 404 if the team is missing
        if (peopleRepository.findByIdAndDeletedFalse(personId).isEmpty()) {
            throw new PersonNotFoundException(personId);
        }
        repository.addMember(teamId, personId);
    }
}
