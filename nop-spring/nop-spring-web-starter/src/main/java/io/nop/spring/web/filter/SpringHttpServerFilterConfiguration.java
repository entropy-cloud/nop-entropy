/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.spring.web.filter;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.OrderedComparator;
import io.nop.http.api.server.HttpServerHelper;
import io.nop.http.api.server.IHttpServerContext;
import io.nop.http.api.server.IHttpServerFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class SpringHttpServerFilterConfiguration {
    private List<IHttpServerFilter> filters;

    public synchronized List<IHttpServerFilter> getFilters() {
        if (filters == null) {
            filters = new ArrayList<>(
                    BeanContainer.instance().getBeansOfType(IHttpServerFilter.class).values());
            Collections.sort(filters, OrderedComparator.instance());
        }
        return filters;
    }

    @Bean
    public FilterRegistrationBean<Filter> registerFilter() {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(((request, response, chain) -> {
            List<IHttpServerFilter> serverFilters = getFilters();

            if (serverFilters.isEmpty()) {
                chain.doFilter(request, response);
            } else {
                IHttpServerContext ctx = new ServletHttpServerContext((HttpServletRequest) request,
                        (HttpServletResponse) response);
                HttpServerHelper.runWithFilters(serverFilters, ctx, () -> {
                    return FutureHelper.futureCall(() -> {
                        chain.doFilter(request, response);
                        return null;
                    });
                });
            }
        }));

//        bean.setOrder(FilterRegistrationBean.HIGHEST_PRECEDENCE + 10000);
        // 设置优先级为正常，系统可以在前面增加过滤器？
        bean.setOrder(0);
        bean.setEnabled(true);
        bean.addUrlPatterns("/*");
        return bean;
    }

}