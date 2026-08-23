package com.ssa.ai.repository;

import com.ssa.ai.model.Skill;

import java.util.List;
import java.util.Optional;

public interface SkillRepository {
    List<Skill> findAll();
    Optional<Skill> findById(String id);
    Skill save(Skill skill);
    void deleteById(String id);
}

