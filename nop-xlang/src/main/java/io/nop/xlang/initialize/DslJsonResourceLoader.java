package io.nop.xlang.initialize;

import io.nop.api.core.util.IComponentModel;
import io.nop.core.lang.json.JObject;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.DslModelParser;

public class DslJsonResourceLoader implements IResourceObjectLoader<IComponentModel> {
    private final String schemaPath;
    private final String resolveInDir;

    public DslJsonResourceLoader(String schemaPath, String resolveInDir) {
        this.schemaPath = schemaPath;
        this.resolveInDir = resolveInDir;
    }

    @Override
    public IComponentModel loadObjectFromPath(String path) {
        return parseFromResource(VirtualFileSystem.instance().getResource(path));
    }

    @Override
    public IComponentModel parseFromResource(IResource resource) {
        Object bean = JsonTool.parseBeanFromResource(resource, JObject.class, true);
        XNode node = DslModelHelper.dslModelToXNode(schemaPath, bean);
        return new DslModelParser(schemaPath).resolveInDir(resolveInDir).parseFromNode(node);
    }
}
