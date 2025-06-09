/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.spring.web.filter;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.OrderedComparator;
import io.nop.http.api.server.HttpServerHelper;
import io.nop.http.api.server.IHttpServerContext;
import io.nop.http.api.server.IHttpServerFilter;
import jakarta.annotation.Nonnull;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "nop.web.http-server-filter.enabled", havingValue = "true", matchIfMissing = true)
public class SpringHttpServerFilterConfiguration {
    private List<IHttpServerFilter> filters;

    @Value("${nop.spring.http-server-filter.sys-order:0}")
    int sysFilterOrder;

    @Value("${nop.spring.http-server-filter.app-order:1000}")
    int appFilterOrder;

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

    private FilterRegistrationBean<Filter> sysFilter;
    private FilterRegistrationBean<Filter> appFilter;

    @Bean
    public FilterRegistrationBean<Filter> registerSysFilter() {
        if (sysFilter == null)
            sysFilter = createFilter(true, sysFilterOrder);
        return sysFilter;
    }

    @Bean
    public FilterRegistrationBean<Filter> registerAppFilter() {
        if (appFilter == null)
            appFilter = createFilter(false, appFilterOrder);
        return appFilter;
    }

    class NopHttpServerFilter extends OncePerRequestFilter {
        private final boolean sys;

        public NopHttpServerFilter(boolean sys) {
            this.sys = sys;
        }

        @Override
        protected String getFilterName() {
            return "nop-web-filter-" + (sys ? "sys" : "app");
        }

        @Override
        protected void doFilterInternal(@Nonnull HttpServletRequest request,
                                        @Nonnull HttpServletResponse response,
                                        @Nonnull FilterChain filterChain) throws ServletException, IOException {
            List<IHttpServerFilter> serverFilters = getFilters(sys);

            if (serverFilters.isEmpty()) {
                filterChain.doFilter(request, response);
            } else {
                IHttpServerContext ctx = new ServletHttpServerContext(request,
                        response);
                CompletionStage<?> future = HttpServerHelper.runWithFilters(serverFilters, ctx, () -> {
                    return FutureHelper.futureCall(() -> {
                        filterChain.doFilter(request, response);
                        return null;
                    });
                });
                FutureHelper.syncGet(future);
            }

        }
    }

    FilterRegistrationBean<Filter> createFilter(boolean sys, int filterOrder) {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new NopHttpServerFilter(sys));

        // 设置优先级为正常，系统可以在前面增加过滤器？
        bean.setOrder(filterOrder);
        bean.setEnabled(true);
        bean.addUrlPatterns("/*");
        return bean;
    }
}