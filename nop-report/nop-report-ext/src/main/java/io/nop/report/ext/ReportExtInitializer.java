package io.nop.report.ext;

import io.nop.api.core.util.ICancellable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

public class ReportExtInitializer {
    private ICancellable cancellable;

    @PostConstruct
    public void init() {
        cancellable = ReportExtFunctions.register();
    }

    @PreDestroy
    public void destroy() {
        if (cancellable != null)
            cancellable.cancel();
    }
}
