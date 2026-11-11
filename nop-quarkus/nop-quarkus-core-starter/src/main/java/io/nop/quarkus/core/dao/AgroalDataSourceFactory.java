package io.nop.quarkus.core.dao;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.AgroalDataSourceMetrics;
import io.agroal.api.configuration.AgroalConnectionPoolConfiguration;
import io.agroal.api.configuration.supplier.AgroalConnectionFactoryConfigurationSupplier;
import io.agroal.api.configuration.supplier.AgroalConnectionPoolConfigurationSupplier;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.dao.jdbc.datasource.DataSourceConfig;
import io.nop.dao.jdbc.datasource.IDataSourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

public class AgroalDataSourceFactory implements IDataSourceFactory {
    static final Logger LOG = LoggerFactory.getLogger(AgroalDataSourceFactory.class);

    @Override
    public AgroalDataSource newDataSource(DataSourceConfig config) {
        AgroalDataSourceConfigurationSupplier dataSourceConfiguration = new AgroalDataSourceConfigurationSupplier();
        applyConfig(dataSourceConfiguration, config);

        AgroalDataSource dataSource = new io.agroal.pool.DataSource(dataSourceConfiguration.get());

        if (config.isMetricsEnabled()) {
            exportMetrics(GlobalMeterRegistry.instance(), config.getName(), dataSource.getMetrics());
        }

        return dataSource;
    }


    void applyConfig(AgroalDataSourceConfigurationSupplier dataSourceConfiguration, DataSourceConfig config) {
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

    /**
     * 4.4-C6-005（plan 2562 裁定 I）：热重载安全重绑——识别键 = meter name + {@code datasource}
     * tag（确定性），重注册前先 remove 旧绑定，避免 Gauge 叠绑（Micrometer 对同名同 tag
     * 重复注册会抛 IllegalArgumentException）。
     */
    static final List<String> AGROAL_METER_NAMES = List.of(
            "agroal.active.count",
            "agroal.available.count",
            "agroal.max.used.count",
            "agroal.awaiting.count",
            "agroal.acquire.count",
            "agroal.creation.count",
            "agroal.leak.detection.count",
            "agroal.destroy.count",
            "agroal.flush.count",
            "agroal.invalid.count",
            "agroal.reap.count",
            "agroal.blocking.time.average",
            "agroal.blocking.time.max",
            "agroal.blocking.time.total",
            "agroal.creation.time.average",
            "agroal.creation.time.max",
            "agroal.creation.time.total");

    void exportMetrics(MeterRegistry metricsFactory, String dataSourceName, AgroalDataSourceMetrics metrics) {
        String tagValue = StringHelper.isEmpty(dataSourceName) ? "default" : dataSourceName;

        // 4.4-C6-005（plan 2562 裁定 I）：判存移除旧绑定后再重注册，热重载不再叠绑

        for (String meterName : AGROAL_METER_NAMES) {
            Meter oldMeter = metricsFactory.find(meterName)
                    .tag("datasource", tagValue).meter();
            if (oldMeter != null) {
                metricsFactory.remove(oldMeter);
            }
        }

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
}
