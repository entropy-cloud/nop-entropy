/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.initializer;

import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.excel.ExcelConstants;
import io.nop.xlang.xdsl.DslModelParser;

public class ExcelCoreInitializer implements ICoreInitializer {
    private final Cancellable cancellable = new Cancellable();

    @Override
    public int order() {
        return CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT;
    }

    @Override
    public void initialize() {
        ComponentModelConfig config = new ComponentModelConfig();
        config.modelType("imp");
        config.loader(ExcelConstants.FILE_TYPE_IMP_XML,
                path -> new DslModelParser(ExcelConstants.XDSL_SCHEMA_IMP).parseFromVirtualPath(path));
        cancellable.append(ResourceComponentManager.instance().registerComponentModelConfig(config));

    }

    @Override
    public void destroy() {
        cancellable.cancel();
    }
}
