/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.initialize;

import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.xlang.xdef.domain.StdDomainRegistry;

public class AiCoreInitializer implements ICoreInitializer {
    private Cancellable cancellable = new Cancellable();

    @Override
    public int order() {
        return CoreConstants.INITIALIZER_PRIORITY_REGISTER_XLANG;
    }

    @Override
    public void initialize() {
        StdDomainRegistry.instance().registerStdDomainHandler(PromptSyntaxStdDomainHandler.INSTANCE);
        cancellable.appendOnCancelTask(() -> {
            StdDomainRegistry.instance().unregisterStdDomainHandler(PromptSyntaxStdDomainHandler.INSTANCE);
        });
    }

    @Override
    public void destroy() {
        cancellable.cancel();
    }
}