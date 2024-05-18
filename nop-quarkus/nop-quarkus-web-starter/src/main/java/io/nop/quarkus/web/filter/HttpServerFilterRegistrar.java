/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.quarkus.web.filter;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.OrderedComparator;
import io.nop.http.api.server.HttpServerHelper;
import io.nop.http.api.server.IHttpServerFilter;
import io.nop.quarkus.web.QuarkusWebConstants;
import io.quarkus.vertx.http.runtime.filters.Filters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.nop.quarkus.web.QuarkusWebConstants.KEY_NOP_HTTP_SERVER_CONTEXT;

@ApplicationScoped
public class HttpServerFilterRegistrar {

    @Inject
    @ConfigProperty(name = "nop.web.http-server-filter.enabled", defaultValue = "true")
    boolean enableFilter;

    @Inject
    @ConfigProperty(name = "nop.quarkus.http-server-filter.sys-order", defaultValue = QuarkusWebConstants.PRIORITY_SYS_FILTER + "")
    int sysFilterOrder;

    @Inject
    @ConfigProperty(name = "nop.quarkus.http-server-filter.app-order", defaultValue = QuarkusWebConstants.PRIORITY_APP_FILTER + "")
    int appFilterOrder;

    private List<IHttpServerFilter> filters;

    public synchronized List<IHttpServerFilter> getFilters(boolean sys) {
        if (filters == null) {
            filters = new ArrayList<>(
                    BeanContainer.instance().getBeansOfType(IHttpServerFilter.class).values());
            Collections.sort(filters, OrderedComparator.instance());
        }

        return filters.stream().filter(filter -> {
            boolean high = filter.order() < IHttpServerFilter.NORMAL_PRIORITY;
            return sys == high;
        }).collect(Collectors.toList());
    }

    public void setupSysFilter(@Observes Filters filters) {
        registerFilter(filters, true);
    }

    public void setupAppFilter(@Observes Filters filters) {
        //registerFilter(filters, false);
    }

    void registerFilter(Filters filters, boolean sys) {
        if (!enableFilter)
            return;

        // 此时Nop平台还没有初始化
        filters.register((rc) -> {
            List<IHttpServerFilter> serverFilters = getFilters(sys);

            if (serverFilters.isEmpty()) {
                rc.next();
            } else {
                VertxHttpServerContext ctx = rc.get(KEY_NOP_HTTP_SERVER_CONTEXT);
                if (ctx == null) {
                    ctx = new VertxHttpServerContext(rc);
                    rc.put(KEY_NOP_HTTP_SERVER_CONTEXT, ctx);
                }
                HttpServerHelper.runWithFilters(serverFilters, ctx, ctx::proceedAsync);
            }
        }, sys ? sysFilterOrder : appFilterOrder);
    }
}