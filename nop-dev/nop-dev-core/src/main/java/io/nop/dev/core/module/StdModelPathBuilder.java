package io.nop.dev.core.module;

import io.nop.commons.util.StringHelper;
import io.nop.core.resource.VirtualFileSystem;

public class StdModelPathBuilder {
    public DevModuleResourcePaths build(String moduleId, String rootPath) {
        DevModuleResourcePaths paths = new DevModuleResourcePaths();
        paths.setModuleId(moduleId);
        paths.setModuleName(moduleId.replace('/', '-'));
        paths.setRootPath(rootPath);

        addPath(paths, "action-auth", paths.getModuleName() + "-web", "/auth/" + paths.getModuleName() + ".action-auth.xml");
        addPath(paths, "data-auth", paths.getModuleName() + "-service", "auth/" + paths.getModuleName() + ".data-auth.xml");
        addPath(paths, "orm", paths.getModuleName() + "-dao", "/orm/app.orm.xml");
        addPath(paths, "beans", paths.getModuleName() + "-service", "/beans/app-service.beans.xml");
        return paths;
    }

    void addPath(DevModuleResourcePaths paths, String modelType, String subModuleName, String path) {
        String subPath = subModuleName + "/src/main/resources/_vfs/" + paths.getModuleId() + path;
        DevResourcePath devPath = new DevResourcePath();
        devPath.setModelType(modelType);
        devPath.setEditorObjName("NopDevEditorFor" + StringHelper.camelCase(modelType, '-', true));
        devPath.setDevResourcePath(StringHelper.appendPath(paths.getRootPath(), subPath));
        devPath.setExists(VirtualFileSystem.instance().getResource(devPath.getDevResourcePath()) != null);
        paths.addModelPath(devPath);
    }
}
