package com.ssa.ai.web;

import com.ssa.ai.model.Skill;
import com.ssa.ai.service.SkillsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/skills")
public class SkillsController {

    private final SkillsService skillsService;

    public SkillsController(SkillsService skillsService) {
        this.skillsService = skillsService;
    }

    @GetMapping
    public List<Skill> list() {
        return skillsService.list();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Skill> get(@PathVariable String id) {
        return skillsService.get(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Skill> create(@RequestBody Skill skill) {
        Skill created = skillsService.create(skill);
        return ResponseEntity.created(URI.create("/api/skills/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Skill> update(@PathVariable String id, @RequestBody Skill skill) {
        Skill updated = skillsService.update(id, skill);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        skillsService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

