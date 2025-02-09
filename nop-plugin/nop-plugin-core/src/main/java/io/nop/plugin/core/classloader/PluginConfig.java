package io.nop.plugin.core.classloader;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.util.StringHelper;

import java.util.List;

@DataBean
public class PluginConfig {
    private List<String> importPackages;
    private List<String> importResources;
    private String pluginClassName;

    public List<String> getImportPackages() {
        return importPackages;
    }

    public void setImportPackages(List<String> importPackages) {
        this.importPackages = importPackages;
    }

    public List<String> getImportResources() {
        return importResources;
    }

    public void setImportResources(List<String> importResources) {
        this.importResources = importResources;
    }

    public boolean shouldImportClass(String className) {
        // Plugin Api中的类总是从外部环境导入
        if (className.startsWith("io.nop.plugin.api"))
            return true;

        if (importPackages != null) {
            for (String pkg : importPackages) {
                if (StringHelper.classInPackage(className, pkg))
                    return true;
            }
        }
        return false;
    }

    public boolean shouldImportResource(String resourceName) {
        if (importResources != null) {
            for (String importResource : importResources) {
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
}
