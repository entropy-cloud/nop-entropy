/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xt.loader;

import io.nop.api.core.util.IComponentModel;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xt.model.XtTransformModel;

public class XtTransformModelLoader implements IResourceObjectLoader<IComponentModel> {
    private static final String XT_SCHEMA_PATH = "/nop/schema/xt.xdef";

    @Override
    public XtTransformModel loadObjectFromPath(String path) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        return loadObjectFromResource(resource);
    }

    @Override
    public XtTransformModel loadObjectFromResource(IResource resource) {
        return (XtTransformModel) new DslModelParser(XT_SCHEMA_PATH).parseFromResource(resource);
    }
}
