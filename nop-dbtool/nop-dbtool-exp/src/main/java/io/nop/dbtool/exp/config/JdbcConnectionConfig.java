package io.nop.dbtool.exp.config;

import io.nop.dao.jdbc.datasource.SimpleDataSource;
import io.nop.dbtool.exp.config._gen._JdbcConnectionConfig;

import javax.sql.DataSource;

public class JdbcConnectionConfig extends _JdbcConnectionConfig {
    public JdbcConnectionConfig() {

    }

    public DataSource buildDataSource() {
        SimpleDataSource ds = new SimpleDataSource();
        ds.setUrl(getJdbcUrl().trim());
        ds.setCatalog(getCatalog());
        ds.setUsername(getUsername());
        ds.setPassword(getPassword());
        ds.setDriverClassName(getDriverClassName().trim());
        return ds;
    }
}
