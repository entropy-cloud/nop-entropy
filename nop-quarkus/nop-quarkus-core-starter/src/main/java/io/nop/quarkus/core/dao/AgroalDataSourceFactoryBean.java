/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.quarkus.core.dao;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.AgroalDataSourceMetrics;
import io.agroal.api.configuration.AgroalConnectionPoolConfiguration;
import io.agroal.api.configuration.supplier.AgroalConnectionFactoryConfigurationSupplier;
import io.agroal.api.configuration.supplier.AgroalConnectionPoolConfigurationSupplier;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;
import io.agroal.pool.DataSource;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.nop.api.core.annotations.ioc.BeanMethod;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.dao.jdbc.datasource.DataSourceConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        if (config.isMetricsEnabled()) {
            exportMetrics(GlobalMeterRegistry.instance(), config.getName(), dataSource);
        }
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

    private void exportMetrics(MeterRegistry metricsFactory, String dataSourceName, AgroalDataSource dataSource) {
        String tagValue = StringHelper.isEmpty(dataSourceName) ? "default" : dataSourceName;
        AgroalDataSourceMetrics metrics = dataSource.getMetrics();

        Gauge.builder("agroal.active.count", metrics::activeCount)
                .description(
                        "Number of active connections. These connections are in use and not available to be acquired.")
                .tag("datasource", tagValue)
                .register(metricsFactory);
        Gauge.builder("agroal.available.count", metrics::availableCount)
                .description("Number of idle connections in the pool, available to be acquired.")
                .tag("datasource", tagValue)
                .register(metricsFactory);
        Gauge.builder("agroal.max.used.count", metrics::maxUsedCount)
                .description("Maximum number of connections active simultaneously.")
                .tag("datasource", tagValue)
                .register(metricsFactory);
        Gauge.builder("agroal.awaiting.count", metrics::awaitingCount)
                .description("Approximate number of threads blocked, waiting to acquire a connection.")
                .tag("datasource", tagValue)
                .register(metricsFactory);

        Gauge.builder("agroal.acquire.count", metrics::acquireCount)
                .description("Number of times an acquire operation succeeded.")
                .tag("datasource", tagValue)
                .register(metricsFactory);
        Gauge.builder("agroal.creation.count", metrics::creationCount)
                .description("Number of created connections.")
                .tag("datasource", tagValue)
                .register(metricsFactory);
        Gauge.builder("agroal.leak.detection.count", metrics::leakDetectionCount)
                .description("Number of times a leak was detected. A single connection can be detected multiple times.")
                .tag("datasource", tagValue)
                .register(metricsFactory);
        Gauge.builder("agroal.destroy.count", metrics::destroyCount)
                .description("Number of destroyed connections.")
                .tag("datasource", tagValue)
                .register(metricsFactory);
        Gauge.builder("agroal.flush.count", metrics::flushCount)
                .description("Number of connections removed from the pool, not counting invalid / idle.")
                .tag("datasource", tagValue)
                .register(metricsFactory);
        Gauge.builder("agroal.invalid.count", metrics::invalidCount)
                .description("Number of connections removed from the pool for being idle.")
                .tag("datasource", tagValue)
                .register(metricsFactory);
        Gauge.builder("agroal.reap.count", metrics::reapCount)
                .description("Number of connections removed from the pool for being idle.")
                .tag("datasource", tagValue)
                .register(metricsFactory);

        Gauge.builder("agroal.blocking.time.average", () -> metrics.blockingTimeAverage().toMillis())
                .description("Average time an application waited to acquire a connection.")
                .tag("datasource", tagValue)
                .register(metricsFactory);
        Gauge.builder("agroal.blocking.time.max", () -> metrics.blockingTimeMax().toMillis())
                .description("Maximum time an application waited to acquire a connection.")
                .tag("datasource", tagValue)
                .register(metricsFactory);
        Gauge.builder("agroal.blocking.time.total", () -> metrics.blockingTimeTotal().toMillis())
                .description("Total time applications waited to acquire a connection.")
                .tag("datasource", tagValue)
                .register(metricsFactory);
        Gauge.builder("agroal.creation.time.average", () -> metrics.creationTimeAverage().toMillis())
                .description("Average time for a connection to be created.")
                .tag("datasource", tagValue)
                .register(metricsFactory);
        Gauge.builder("agroal.creation.time.max", () -> metrics.creationTimeMax().toMillis())
                .description("Maximum time for a connection to be created.")
                .tag("datasource", tagValue)
                .register(metricsFactory);
        Gauge.builder("agroal.creation.time.total", () -> metrics.creationTimeTotal().toMillis())
                .description("Total time waiting for connections to be created.")
                .tag("datasource", tagValue)
                .register(metricsFactory);
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
