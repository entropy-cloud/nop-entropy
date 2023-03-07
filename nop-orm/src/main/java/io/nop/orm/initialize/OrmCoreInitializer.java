/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.initialize;

import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.orm.OrmConstants;
import io.nop.orm.pdm.PdmModelParser;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.DslModelParser;

public class OrmCoreInitializer implements ICoreInitializer {
    private Cancellable cancellable = new Cancellable();

    @Override
    public int order() {
        return CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT;
    }

    @Override
    public void initialize() {
        ComponentModelConfig config = new ComponentModelConfig();
        config.modelType("orm");
        config.loader(OrmConstants.FILE_TYPE_ORM_XML,
                path -> new DslModelParser(OrmConstants.XDSL_SCHEMA_ORM).parseFromVirtualPath(path));
        config.loader(OrmConstants.FILE_TYPE_PDM, path -> new PdmModelParser().parseFromVirtualPath(path));
        if (DslModelHelper.supportExcelModelLoader()) {
            config.loader(OrmConstants.FILE_TYPE_ORM_XLSX,
                    DslModelHelper.newExcelModelLoader(OrmConstants.ORM_IMP_MODEL_PATH));
        }
        cancellable.append(ResourceComponentManager.instance().registerComponentModelConfig(config));
    }

    @Override
    public void destroy() {
        cancellable.cancel();
    }
}
