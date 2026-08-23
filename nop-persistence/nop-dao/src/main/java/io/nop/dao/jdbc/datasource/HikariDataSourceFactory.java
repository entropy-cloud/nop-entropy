package io.nop.dao.jdbc.datasource;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.CollectionHelper;

import javax.sql.DataSource;

public class HikariDataSourceFactory implements IDataSourceFactory {
    @Override
    public DataSource newDataSource(DataSourceConfig config) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setUsername(config.getUsername());
        dataSource.setPassword(config.getPassword());
        dataSource.setJdbcUrl(config.getJdbcUrl());
        dataSource.setMaximumPoolSize(config.getMaxSize());
        dataSource.setMinimumIdle(config.getMinSize());
        if (config.getConnectionTimeout() != null)
            dataSource.setConnectionTimeout(config.getConnectionTimeout().toMillis());
        if (config.getIdleTimeout() != null)
            dataSource.setIdleTimeout(config.getIdleTimeout().toMillis());
        if (config.getMaxLifetime() != null)
            dataSource.setMaxLifetime(config.getMaxLifetime().toMillis());
        if (config.getValidationQuerySql() != null)
            dataSource.setConnectionTestQuery(config.getValidationQuerySql());
        dataSource.setConnectionInitSql(config.getConnectionInitSql());
        if (config.getDriverClassName() != null)
            dataSource.setDriverClassName(config.getDriverClassName());
        dataSource.setPoolName(config.getName());

        // Hikari没有后台校验选项，最接近的语义是keepaliveTime定期探活。keepalive必须小于maxLifetime，
        // 否则Hikari启动时会抛异常，这里直接跳过无效配置
        if (config.getBackgroundValidationInterval() != null) {
            long keepalive = config.getBackgroundValidationInterval().toMillis();
            long maxLifetime = config.getMaxLifetime() != null ? config.getMaxLifetime().toMillis()
                    : dataSource.getMaxLifetime();
            if (keepalive > 0 && keepalive < maxLifetime)
                dataSource.setKeepaliveTime(keepalive);
        }

        if (config.isMetricsEnabled())
            dataSource.setMetricRegistry(GlobalMeterRegistry.instance());

        if (config.getProperties() != null)
            dataSource.setDataSourceProperties(CollectionHelper.mapToProperties(config.getProperties()));
        return dataSource;
    }
}
