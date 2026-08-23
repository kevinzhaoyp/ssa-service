package com.ssa.plugin;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PluginDescriptor {
    private String key;
    private String version;
    private String mainClass;
    private String routePrefix;
    private List<String> dependencies;
    private String author;
    private String releaseDate;
    private String description;
}
