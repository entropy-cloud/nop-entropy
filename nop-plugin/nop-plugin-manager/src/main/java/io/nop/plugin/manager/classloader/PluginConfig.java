package io.nop.plugin.manager.classloader;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.util.StringHelper;

import java.util.List;
import java.util.Set;

@DataBean
public class PluginConfig {
    private String pluginClassName;

    private List<String> importPackages;
    private List<String> importResourceDirs;

    private Set<String> excludeImportClasses;
    private Set<String> excludeImportResourceFiles;
    private List<String> excludeImportPackages;
    private List<String> excludeImportResourceDirs;

    public List<String> getImportPackages() {
        return importPackages;
    }

    public void setImportPackages(List<String> importPackages) {
        this.importPackages = importPackages;
    }

    public List<String> getImportResourceDirs() {
        return importResourceDirs;
    }

    public void setImportResourceDirs(List<String> importResourceDirs) {
        this.importResourceDirs = importResourceDirs;
    }

    public boolean shouldImportClass(String className) {
        // Plugin Api中的类总是从外部环境导入
        if (className.startsWith("io.nop.plugin.api"))
            return true;

        if (excludeImportClasses != null) {
            if (excludeImportClasses.contains(className))
                return false;
        }

        if (excludeImportPackages != null) {
            for (String pkg : excludeImportPackages) {
                if (StringHelper.classInPackage(className, pkg))
                    return false;
            }
        }

        if (importPackages != null) {
            for (String pkg : importPackages) {
                if (StringHelper.classInPackage(className, pkg))
                    return true;
            }
        }
        return false;
    }

    public boolean shouldImportResource(String resourceName) {
        if (excludeImportResourceFiles != null) {
            if (excludeImportResourceFiles.contains(resourceName))
                return false;
        }

        if (excludeImportResourceDirs != null) {
            for (String importResource : excludeImportResourceDirs) {
                if (StringHelper.pathStartsWith(resourceName, importResource))
                    return false;
            }
        }

        if (importResourceDirs != null) {
            for (String importResource : importResourceDirs) {
                if (StringHelper.pathStartsWith(resourceName, importResource))
                    return true;
            }
        }
        return false;
    }

    public String getPluginClassName() {
        return pluginClassName;
    }

    public void setPluginClassName(String pluginClassName) {
        this.pluginClassName = pluginClassName;
    }

    public Set<String> getExcludeImportClasses() {
        return excludeImportClasses;
    }

    public void setExcludeImportClasses(Set<String> excludeImportClasses) {
        this.excludeImportClasses = excludeImportClasses;
    }

    public Set<String> getExcludeImportResourceFiles() {
        return excludeImportResourceFiles;
    }

    public void setExcludeImportResourceFiles(Set<String> excludeImportResourceFiles) {
        this.excludeImportResourceFiles = excludeImportResourceFiles;
    }

    public List<String> getExcludeImportPackages() {
        return excludeImportPackages;
    }

    public void setExcludeImportPackages(List<String> excludeImportPackages) {
        this.excludeImportPackages = excludeImportPackages;
    }

    public List<String> getExcludeImportResourceDirs() {
        return excludeImportResourceDirs;
    }

    public void setExcludeImportResourceDirs(List<String> excludeImportResourceDirs) {
        this.excludeImportResourceDirs = excludeImportResourceDirs;
    }
}
