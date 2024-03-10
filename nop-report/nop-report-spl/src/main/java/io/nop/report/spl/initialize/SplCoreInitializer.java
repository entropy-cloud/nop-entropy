/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.spl.initialize;

import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.report.spl.SplConstants;
import io.nop.report.spl.model.SplModelLoader;

public class SplCoreInitializer implements ICoreInitializer {
    private final Cancellable cancellable = new Cancellable();

    @Override
    public int order() {
        return CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT;
    }

    @Override
    public void initialize() {
        ComponentModelConfig config = new ComponentModelConfig();
        config.setModelType(SplConstants.MODEL_TYPE_SPL);
        SplModelLoader loader = new SplModelLoader();
        config.loader(SplConstants.FILE_TYPE_SPLX, loader);
        config.loader(SplConstants.FILE_TYPE_SPL, loader);
        cancellable.append(ResourceComponentManager.instance().registerComponentModelConfig(config));
    }

    @Override
    public void destroy() {
        cancellable.cancel();
    }
}
