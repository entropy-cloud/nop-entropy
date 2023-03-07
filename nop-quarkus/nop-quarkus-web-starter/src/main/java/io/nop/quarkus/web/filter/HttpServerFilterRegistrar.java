/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.quarkus.web.filter;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.OrderedComparator;
import io.nop.http.api.server.HttpServerHelper;
import io.nop.http.api.server.IHttpServerFilter;
import io.nop.quarkus.web.QuarkusWebConstants;
import io.quarkus.vertx.http.runtime.filters.Filters;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class HttpServerFilterRegistrar {
    private List<IHttpServerFilter> filters;

    public synchronized List<IHttpServerFilter> getFilters() {
        if (filters == null) {
            filters = new ArrayList<>(
                    BeanContainer.instance().getBeansOfType(IHttpServerFilter.class).values());
            Collections.sort(filters, OrderedComparator.instance());
        }
        return filters;
    }

    public void setupFilter(@Observes Filters filters) {
        // 此时Nop平台还没有初始化
        filters.register((rc) -> {
            List<IHttpServerFilter> serverFilters = getFilters();

            if (serverFilters.isEmpty()) {
                rc.next();
            } else {
                VertxHttpServerContext ctx = new VertxHttpServerContext(rc);
                HttpServerHelper.runWithFilters(serverFilters, ctx, ctx::proceedAsync);
            }
        }, QuarkusWebConstants.PRIORITY_APP_FILTER);
    }
}