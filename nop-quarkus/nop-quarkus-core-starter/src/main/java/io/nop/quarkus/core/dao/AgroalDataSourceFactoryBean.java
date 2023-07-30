/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.quarkus.core.dao;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.AgroalConnectionPoolConfiguration;
import io.agroal.api.configuration.supplier.AgroalConnectionFactoryConfigurationSupplier;
import io.agroal.api.configuration.supplier.AgroalConnectionPoolConfigurationSupplier;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;
import io.agroal.pool.DataSource;
import io.nop.api.core.annotations.ioc.BeanMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

public class AgroalDataSourceFactoryBean {
    static final Logger LOG = LoggerFactory.getLogger(AgroalDataSourceFactoryBean.class);

    private AgroalDataSource dataSource;

    private DataSourceConfig config;

    public void setConfig(DataSourceConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        AgroalDataSourceConfigurationSupplier dataSourceConfiguration = new AgroalDataSourceConfigurationSupplier();
        applyConfig(dataSourceConfiguration);

        this.dataSource = new DataSource(dataSourceConfiguration.get());
    }

    void applyConfig(AgroalDataSourceConfigurationSupplier dataSourceConfiguration) {
        AgroalConnectionPoolConfigurationSupplier poolConfiguration = dataSourceConfiguration
                .connectionPoolConfiguration();
        AgroalConnectionFactoryConfigurationSupplier connectionFactoryConfiguration = poolConfiguration
                .connectionFactoryConfiguration();

        connectionFactoryConfiguration.jdbcUrl(config.getJdbcUrl());
        connectionFactoryConfiguration.connectionProviderClassName(config.getDriverClassName());
        connectionFactoryConfiguration.trackJdbcResources(true);
        dataSourceConfiguration.metricsEnabled(config.isMetricsEnabled());

        connectionFactoryConfiguration.principal(new NamePrincipal(config.getUsername()));
        connectionFactoryConfiguration.credential(new SimplePassword(config.getPassword()));

        if (config.getProperties() != null) {
            for (Map.Entry<String, String> entry : config.getProperties().entrySet()) {
                connectionFactoryConfiguration.jdbcProperty(entry.getKey(), entry.getValue());
            }
        }

        poolConfiguration.maxSize(config.getMaxSize());
        if (config.getMinSize() > 0) {
            poolConfiguration.minSize(config.getMinSize());
        }

        if (config.getInitialSize() > 0) {
            poolConfiguration.initialSize(config.getInitialSize());
        }

        if (config.getConnectionTimeout() != null) {
            poolConfiguration.acquisitionTimeout(config.getConnectionTimeout());
        }

        poolConfiguration.connectionValidator(AgroalConnectionPoolConfiguration.ConnectionValidator.defaultValidator());

        // Connection management
        if (config.getBackgroundValidationInterval() != null) {
            poolConfiguration.validationTimeout(config.getBackgroundValidationInterval());
        }

        if (config.getValidationQuerySql() != null) {
            String validationQuery = config.getValidationQuerySql();
            poolConfiguration.connectionValidator(new AgroalConnectionPoolConfiguration.ConnectionValidator() {

                @Override
                public boolean isValid(Connection connection) {
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute(validationQuery);
                        return true;
                    } catch (Exception e) {
                        LOG.warn("nop.dao.connection-validation-fail", e);
                    }
                    return false;
                }
            });
        }
        if (config.getIdleTimeout() != null) {
            poolConfiguration.reapTimeout(config.getIdleTimeout());
        }

        if (config.getMaxLifetime() != null) {
            poolConfiguration.maxLifetime(config.getMaxLifetime());
        }
    }

    @PreDestroy
    public void destroy() {
        dataSource.close();
    }

    @BeanMethod
    public javax.sql.DataSource get() {
        return dataSource;
    }
}
