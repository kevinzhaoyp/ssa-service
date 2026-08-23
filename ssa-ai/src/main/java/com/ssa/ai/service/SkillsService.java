package com.ssa.ai.service;

import com.ssa.ai.model.Skill;
import com.ssa.ai.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class SkillsService {

    private final SkillRepository repository;

    public SkillsService(SkillRepository repository) {
        this.repository = repository;
    }

    public List<Skill> list() {
        return repository.findAll();
    }

    public Optional<Skill> get(String id) {
        return repository.findById(id);
    }

    public Skill create(Skill skill) {
        if (skill == null) {
            throw new IllegalArgumentException("Skill cannot be null");
        }
        if (!StringUtils.hasText(skill.getName())) {
            throw new IllegalArgumentException("Skill name is required");
        }
        skill.setCreatedAt(Instant.now());
        skill.setUpdatedAt(Instant.now());
        return repository.save(skill);
    }

    public Skill update(String id, Skill skill) {
        Skill existing = repository.findById(id).orElseThrow(() -> new NoSuchElementException("Skill not found: " + id));
        if (StringUtils.hasText(skill.getName())) {
            existing.setName(skill.getName());
        }
        if (skill.getDescription() != null) {
            existing.setDescription(skill.getDescription());
        }
        if (skill.getMetadata() != null) {
            existing.setMetadata(skill.getMetadata());
        }
        existing.setUpdatedAt(Instant.now());
        return repository.save(existing);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }
}

