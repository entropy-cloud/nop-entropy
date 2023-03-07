/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.initialize.impl;

import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.CoreConstants;
import io.nop.core.dict.DictModelParser;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;

public class CoreInitializer implements ICoreInitializer {
    private Cancellable cancellable = new Cancellable();

    @Override
    public void initialize() {
        registerDict();
    }

    public int order() {
        return CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT;
    }

    private void registerDict() {
        ComponentModelConfig config = new ComponentModelConfig();
        config.modelType("dict");
        config.loader("dict.yaml", path -> new DictModelParser().parseFromVirtualPath(path));
        cancellable.append(ResourceComponentManager.instance().registerComponentModelConfig(config));
    }

    @Override
    public void destroy() {
        cancellable.cancel();
    }
}
