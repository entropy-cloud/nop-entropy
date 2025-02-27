package io.nop.dao.jdbc.datasource;

import javax.sql.DataSource;

public interface IDataSourceFactory {
    DataSource newDataSource(DataSourceConfig config);
}