/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package org.beetl.sql.jmh.xorm;

import io.nop.dao.txn.ITransactionListener;
import org.beetl.sql.jmh.DataSourceHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import javax.sql.DataSource;

@ComponentScan(basePackages = {"org.beetl.sql.jmh.xorm", "io.nop.spring.autoconfig"})
public class NopAppConfig {
    @Bean
    public DataSource dataSource() {
        return DataSourceHelper.ins();
    }

    @Bean
    public ITransactionListener defaultListener() {
        return new ITransactionListener() {
            @Override
            public int compareTo(ITransactionListener o) {
                return ITransactionListener.super.compareTo(o);
            }
        };
    }
}
