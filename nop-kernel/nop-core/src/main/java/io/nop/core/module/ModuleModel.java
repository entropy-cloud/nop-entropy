/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.module;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.resource.ResourceHelper;

import java.util.Set;

@DataBean
public class ModuleModel {
    private String moduleId;
    private String displayName;
    private String description;
    private String version;
    private Set<String> dependsOn;
    private String author;
    private String publishDate;

    private boolean dynamic;
    private String sid;

    public ModuleModel() {
    }

    public static ModuleModel forModuleName(String moduleName) {
        ModuleModel model = new ModuleModel();
        model.setModuleName(moduleName);
        return model;
    }

    public String getModuleName() {
        return ResourceHelper.getModuleNameFromModuleId(moduleId);
    }

    public void setModuleName(String moduleName) {
        this.moduleId = ResourceHelper.getModuleIdFromModuleName(moduleName);
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    @JsonIgnore
    public String getModuleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    public boolean isDynamic() {
        return dynamic;
    }

    public void setDynamic(boolean dynamic) {
        this.dynamic = dynamic;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(String publishDate) {
        this.publishDate = publishDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<String> getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(Set<String> dependsOn) {
        this.dependsOn = dependsOn;
    }
}