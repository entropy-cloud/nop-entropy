/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.initialize;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.IComponentModel;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.XLangOutputMode;

public class XplModelLoader implements IResourceObjectLoader<IComponentModel> {
    private final XLangOutputMode outputMode;

    public XplModelLoader(XLangOutputMode outputMode) {
        this.outputMode = Guard.notNull(outputMode, "outputMode");
    }

    @Override
    public IComponentModel loadObjectFromPath(String path) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        return XLang.parseXpl(resource, outputMode);
    }
}
