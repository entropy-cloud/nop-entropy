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
        dataSource.setConnectionInitSql(config.getConnectionInitSql());
        dataSource.setDriverClassName(config.getDriverClassName());
        dataSource.setPoolName(config.getName());

        if (config.isMetricsEnabled())
            dataSource.setMetricRegistry(GlobalMeterRegistry.instance());

        if (config.getProperties() != null)
            dataSource.setDataSourceProperties(CollectionHelper.mapToProperties(config.getProperties()));
        return dataSource;
    }
}
