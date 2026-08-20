package com.skillatlas.skills;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skillatlas.skills.domain.Skill;
import com.skillatlas.skills.dto.SkillCatalogResponse;
import com.skillatlas.skills.dto.SkillCreateRequest;
import com.skillatlas.skills.dto.SkillUpdateRequest;
import com.skillatlas.skills.enums.SkillCategory;
import com.skillatlas.skills.exception.SkillNameAlreadyExistsException;
import com.skillatlas.skills.exception.SkillNotFoundException;

@Service
public class SkillsService {

    private final SkillsRepository repository;
    private final SkillsCatalogRepository catalogRepository;

    public SkillsService(SkillsRepository repository, SkillsCatalogRepository catalogRepository) {
        this.repository = repository;
        this.catalogRepository = catalogRepository;
    }

    @Transactional(readOnly = true)
    public Skill getById(String id) {
        return repository.findById(id).orElseThrow(() -> new SkillNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<SkillCatalogResponse> list(String search, SkillCategory category, SkillSort sort,
            Pageable pageable) {
        String s = normalise(search);
        List<SkillCatalogResponse> content =
                catalogRepository.find(s, category, sort, pageable.getOffset(), pageable.getPageSize());
        return new PageImpl<>(content, pageable, catalogRepository.count(s, category));
    }

    @Transactional
    public Skill create(SkillCreateRequest request) {
        if (repository.existsByName(request.name())) {
            throw new SkillNameAlreadyExistsException(request.name());
        }
        Skill skill = new Skill();
        skill.setName(request.name());
        skill.setCategory(request.category());
        skill.setColor(request.color());
        return repository.save(skill);
    }

    @Transactional
    public Skill update(String id, SkillUpdateRequest request) {
        Skill skill = getById(id);
        if (!skill.getName().equals(request.name()) && repository.existsByName(request.name())) {
            throw new SkillNameAlreadyExistsException(request.name());
        }
        skill.setName(request.name());
        skill.setCategory(request.category());
        skill.setColor(request.color());
        return repository.save(skill);
    }

    @Transactional
    public void delete(String id) {
        Skill skill = getById(id);
        repository.delete(skill);
    }

    private static String normalise(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }
}
