package io.nop.xlang.initialize;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JObject;
import io.nop.core.lang.json.JsonSaveOptions;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.xlang.xdsl.AbstractDslResourcePersister;
import io.nop.xlang.xdsl.DslNodeLoader;
import io.nop.xlang.xdsl.IDslTextSerializer;

public class DslJsonResourceLoader extends AbstractDslResourcePersister implements IDslTextSerializer {

    public DslJsonResourceLoader(String schemaPath, String resolveInDir) {
        super(schemaPath, resolveInDir);
    }

    public DslJsonResourceLoader(String schemaPath, String resolveInDir, boolean dynamic) {
        super(schemaPath, resolveInDir, dynamic);
    }

    @Override
    public XNode loadDslNodeFromResource(IResource resource, ResolvePhase phase) {
        Object bean = JsonTool.parseBeanFromResource(resource, JObject.class, true);
        XNode node = transformBeanToNode(bean);
        return DslNodeLoader.INSTANCE.processDslNode(node, this.schemaPath, phase);
    }

    @Override
    public void saveObjectToResource(IResource resource, Object obj) {
        JsonSaveOptions options = new JsonSaveOptions();
        JsonTool.instance().saveToResource(resource, obj, options);
    }

    @Override
    public String serializeToText(String fileType, Object bean) {
        String fileExt = StringHelper.lastPart(fileType, '.');

        if (JsonTool.isYamlFileExt(fileExt)) {
            return JsonTool.serializeToYaml(bean);
        }

        return JsonTool.serialize(bean, true);
    }
}
