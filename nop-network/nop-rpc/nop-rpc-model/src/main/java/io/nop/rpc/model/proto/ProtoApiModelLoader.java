package io.nop.rpc.model.proto;

import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.rpc.model.ApiModel;
import io.nop.rpc.model.RpcModelConstants;
import io.nop.xlang.xdsl.AbstractDslResourceLoader;

public class ProtoApiModelLoader extends AbstractDslResourceLoader<ApiModel> {
    public ProtoApiModelLoader() {
        super(RpcModelConstants.XDSL_SCHEMA_API, null);
    }

    @Override
    public ApiModel loadObjectFromResource(IResource resource) {
        return new ProtoFileParser().parseFromResource(resource);
    }

    @Override
    public XNode loadDslNodeFromResource(IResource resource, ResolvePhase phase) {
        ApiModel model = loadObjectFromResource(resource);
        return transformBeanToNode(model);
    }
}