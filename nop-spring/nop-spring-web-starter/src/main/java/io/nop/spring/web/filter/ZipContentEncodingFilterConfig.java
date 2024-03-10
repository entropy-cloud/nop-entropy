/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.spring.web.filter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "nop.web.zip-content-encoding-filter.enabled", havingValue = "true", matchIfMissing = true)
public class ZipContentEncodingFilterConfig {

    @Bean
    public FilterRegistrationBean<ZipContentEncodingFilter> registerZipContentEncodingFiler() {
        ZipContentEncodingFilter filter = new ZipContentEncodingFilter();
        FilterRegistrationBean<ZipContentEncodingFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(filter);
        bean.setEnabled(true);
        bean.addUrlPatterns("/*");
        return bean;
    }
}
