package io.nop.web.page.initialize;

import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.xlang.xdef.domain.StdDomainRegistry;

public class WebPageCoreInitializer implements ICoreInitializer {
    private Cancellable cancellable = new Cancellable();

    @Override
    public int order() {
        return CoreConstants.INITIALIZER_PRIORITY_REGISTER_XLANG;
    }

    @Override
    public void initialize() {
        StdDomainRegistry.instance().registerStdDomainHandler(VueNodeStdDomainHandler.INSTANCE);
        cancellable.appendOnCancelTask(() -> {
            StdDomainRegistry.instance().unregisterStdDomainHandler(VueNodeStdDomainHandler.INSTANCE);
        });

    }

    @Override
    public void destroy() {
        cancellable.cancel();
    }
}
