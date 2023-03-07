/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.http.api.server;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public class HttpServerHelper {
    public static CompletionStage<Void> runWithFilters(List<IHttpServerFilter> filters,
                                                       IHttpServerContext routeContext, Supplier<CompletionStage<Void>> next) {
        return _runWithFilters(filters, 0, routeContext, next);
    }

    static CompletionStage<Void> _runWithFilters(List<IHttpServerFilter> filters, int index,
                                                 IHttpServerContext routeContext, Supplier<CompletionStage<Void>> next) {
        if (index >= filters.size())
            return next.get();

        return filters.get(index).filterAsync(routeContext, () -> {
            return _runWithFilters(filters, index + 1, routeContext, next);
        });
    }
}
