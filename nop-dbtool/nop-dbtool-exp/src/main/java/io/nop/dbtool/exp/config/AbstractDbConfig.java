package io.nop.dbtool.exp.config;

import io.nop.dao.jdbc.datasource.SimpleDataSource;

import javax.sql.DataSource;

public class AbstractDbConfig {
    private String driverClassName;
    private String jdbcUrl;

    private String userName;

    private String password;

    private String catalog;

    private int threadCount;

    private int batchSize = 100;

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCatalog() {
        return catalog;
    }

    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }

    public DataSource buildDataSource() {
        SimpleDataSource ds = new SimpleDataSource();
        ds.setUrl(jdbcUrl);
        ds.setCatalog(catalog);
        ds.setUsername(userName);
        ds.setPassword(password);
        ds.setDriverClassName(driverClassName);
        return ds;
    }
}
