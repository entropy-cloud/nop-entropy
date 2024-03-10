/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dev.core.store;

import io.nop.api.core.util.IComponentModel;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.dev.core.store.IDevModelStore;
import io.nop.xlang.delta.DeltaDiffer;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xdsl.DslNodeLoader;
import io.nop.xlang.xdsl.XDslExtendResult;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xmeta.SchemaLoader;

import java.util.Map;

public class DevModelStoreImpl implements IDevModelStore {

    protected IResource getResource(String path) {
        return VirtualFileSystem.instance().getResource(path);
    }

    @Override
    public Map<String, Object> loadModel(String path) {
        IResource resource = getResource(path);
        IComponentModel model = DslModelHelper.loadDslModelAsJson(resource, true);
        return (Map<String, Object>) JsonTool.serializeToJson(model);
    }

    @Override
    public void saveModel(String path, Map<String, Object> data) {
        ComponentModelConfig config = ResourceComponentManager.instance().getModelConfigByModelPath(path);
        XNode node = DslModelHelper.dslModelToXNode(config.getXdefPath(), data);
        IResource resource = getResource(path);
        if (resource.exists()) {
            node = buildDelta(node, SchemaLoader.loadXDefinition(config.getXdefPath()), resource);
        }
        validateNode(node);
        ResourceHelper.writeXml(resource, node);
    }

    private void validateNode(XNode node) {
        new DslModelParser().parseFromNode(node);
    }

    XNode buildDelta(XNode node, IXDefNode defNode, IResource resource) {
        XDslExtendResult result = DslNodeLoader.INSTANCE.loadFromResource(resource);
        if (result.getBase() != null) {
            XDslKeys keys = XDslKeys.of(node);
            new DeltaDiffer(keys).diff(node, result.getBase(), defNode, false);
            if (result.getExtendsPath() != null)
                node.setAttr(keys.EXTENDS, result.getExtendsPath());
            if (result.getGenExtends() != null) {
                node.prependChild(result.getGenExtends().cloneInstance());
            }
        }
        return node;
    }
}
