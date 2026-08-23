package com.ssa.ai.repository;

import com.ssa.ai.model.Skill;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemorySkillRepository implements SkillRepository {

    private final ConcurrentMap<String, Skill> store = new ConcurrentHashMap<>();

    @Override
    public List<Skill> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Optional<Skill> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Skill save(Skill skill) {
        if (skill.getId() == null || skill.getId().trim().isEmpty()) {
            skill.setId(java.util.UUID.randomUUID().toString());
        }
        skill.setUpdatedAt(Instant.now());
        if (skill.getCreatedAt() == null) {
            skill.setCreatedAt(Instant.now());
        }
        store.put(skill.getId(), skill);
        return skill;
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
    }
}
