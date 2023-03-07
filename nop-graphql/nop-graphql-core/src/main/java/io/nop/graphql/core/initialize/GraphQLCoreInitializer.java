/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.initialize;

import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.DslModelParser;

public class GraphQLCoreInitializer implements ICoreInitializer {
    private final Cancellable cancellable = new Cancellable();

    @Override
    public int order() {
        return CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT;
    }

    @Override
    public void initialize() {
        ComponentModelConfig config = new ComponentModelConfig();
        config.modelType(GraphQLConstants.DSL_TYPE_API);
        config.loader(GraphQLConstants.DSL_TYPE_API, path -> new DslModelParser(XLangConstants.XDSL_SCHEMA_API).parseFromVirtualPath(path));
        if (DslModelHelper.supportExcelModelLoader()) {
            config.loader(GraphQLConstants.FILE_TYPE_API_XLSX,
                    DslModelHelper.newExcelModelLoader(GraphQLConstants.API_IMP_MODEL_PATH));
        }
        cancellable.append(ResourceComponentManager.instance().registerComponentModelConfig(config));
    }

    @Override
    public void destroy() {
        cancellable.cancel();
    }
}
