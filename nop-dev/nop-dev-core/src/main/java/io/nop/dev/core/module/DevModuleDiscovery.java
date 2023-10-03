package io.nop.dev.core.module;

import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;

import java.util.ArrayList;
import java.util.List;

public class DevModuleDiscovery {
    public List<DevModuleResourcePaths> discover(String rootPath) {
        List<? extends IResource> resources = VirtualFileSystem.instance().getChildren(rootPath);

        List<DevModuleResourcePaths> list = new ArrayList<>();

        String moduleId = findModuleId(resources);
        if (moduleId != null) {
            list.add(new StdModelPathBuilder().build(moduleId, rootPath));
        }

        for (IResource resource : resources) {
            List<? extends IResource> children = VirtualFileSystem.instance().getChildren(resource.getStdPath());
            String subModuleId = findModuleId(children);
            if (subModuleId != null) {
                list.add(new StdModelPathBuilder().build(subModuleId, resource.getStdPath()));
            }
        }

        return list;
    }

    private String findModuleId(List<? extends IResource> resources) {
        boolean hasDao = false, hasService = false, hasWeb = false;
        String moduleId = null;
        for (IResource resource : resources) {
            if (resource.getPath().endsWith("-dao")) {
                hasDao = true;
            } else if (resource.getPath().endsWith("-service")) {
                hasService = true;
                moduleId = StringHelper.removeEnd(resource.getName(), "-service").replace('-', '/');
            } else if (resource.getPath().endsWith("-web")) {
                hasWeb = true;
            }
        }
        boolean b = hasDao && hasService && hasWeb;
        if (!b)
            return null;
        return moduleId;
    }
}
