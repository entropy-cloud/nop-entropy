/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.quarkus.core.dao;

import io.agroal.api.AgroalDataSource;
import io.nop.api.core.annotations.ioc.BeanMethod;
import io.nop.dao.jdbc.datasource.DataSourceConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgroalDataSourceFactoryBean {
    static final Logger LOG = LoggerFactory.getLogger(AgroalDataSourceFactoryBean.class);

    private AgroalDataSource dataSource;

    private DataSourceConfig config;

    public void setConfig(DataSourceConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {

        this.dataSource = new AgroalDataSourceFactory().newDataSource(config);
    }


    @PreDestroy
    public void destroy() {
        if (dataSource != null)
            dataSource.close();
    }

    @BeanMethod
    public javax.sql.DataSource get() {
        return dataSource;
    }
}
