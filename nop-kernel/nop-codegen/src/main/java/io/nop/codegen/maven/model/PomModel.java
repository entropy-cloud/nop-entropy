/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.maven.model;

import io.nop.commons.util.StringHelper;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PomModel implements Serializable {
    File pomFile;

    PomArtifactKey artifactKey;
    String version;
    String packaging;
    PomParentModel parent;
    List<String> modules;

    List<PomModel> resolvedModules;

    Map<String, String> properties;

    Map<PomArtifactKey, PomDependencyModel> dependencies = new LinkedHashMap<>();
    Map<PomArtifactKey, PomDependencyModel> dependencyManagements = new LinkedHashMap<>();
    boolean platform;

    public boolean isPlatformRoot() {
        return platform;
    }

    public void setPlatformRoot(boolean platformRoot) {
        this.platform = platformRoot;
    }

    public File getPomFile() {
        return pomFile;
    }

    public void setPomFile(File pomFile) {
        this.pomFile = pomFile;
    }

    public File getModuleDir() {
        if (pomFile == null)
            return null;
        return pomFile.getParentFile();
    }

    public String getProperty(String name) {
        if (properties == null)
            return null;
        return properties.get(name);
    }

    public void setProperty(String name, String value) {
        if (properties == null) {
            properties = new HashMap<>();
        }
        properties.put(name, value);
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public List<PomModel> getResolvedModules() {
        return resolvedModules;
    }

    public void setResolvedModules(List<PomModel> resolvedModules) {
        this.resolvedModules = resolvedModules;
    }

    public String getGroupId() {
        return artifactKey.getGroupId();
    }

    public String getArtifactId() {
        return artifactKey.getArtifactId();
    }

    public String getEntropyModuleId() {
        String artifactId = getArtifactId();
        if (artifactId.indexOf('-') > 0) {
            artifactId = artifactId.replace('-', '/');
        }
        if (StringHelper.countChar(artifactId, '/') == 1) {
            return artifactId;
        }
        return null;
    }

    public PomArtifactKey getArtifactKey() {
        return artifactKey;
    }

    public void setArtifactKey(PomArtifactKey artifactKey) {
        this.artifactKey = artifactKey;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getPackaging() {
        return packaging;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    public PomParentModel getParent() {
        return parent;
    }

    public void setParent(PomParentModel parent) {
        this.parent = parent;
    }

    public List<String> getModules() {
        return modules;
    }

    public void setModules(List<String> modules) {
        this.modules = modules;
    }

    public void addDependency(PomDependencyModel dependency) {
        this.dependencies.put(dependency.getArtifactKey(), dependency);
    }

    public Map<PomArtifactKey, PomDependencyModel> getDependencies() {
        return dependencies;
    }

    public PomDependencyModel getDependency(PomArtifactKey artifactKey) {
        return dependencies.get(artifactKey);
    }

    public void addDependencies(Collection<PomDependencyModel> deps) {
        if (deps != null) {
            for (PomDependencyModel dep : deps) {
                addDependency(dep);
            }
        }
    }

    public Map<PomArtifactKey, PomDependencyModel> getDependencyManagements() {
        return dependencyManagements;
    }

    public void addDependencyManagement(PomDependencyModel dependency) {
        this.dependencyManagements.put(dependency.getArtifactKey(), dependency);
    }
}