package com.ssa.ai.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Setter
@Getter
public class Skill {
    private String id;
    private String name;
    private String description;
    private boolean enabled = true;
    private Map<String, String> metadata;
    private Instant createdAt;
    private Instant updatedAt;

    public Skill() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Skill(String name, String description, Map<String, String> metadata) {
        this();
        this.name = name;
        this.description = description;
        this.metadata = metadata;
    }

}

