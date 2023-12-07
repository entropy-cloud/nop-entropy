/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.jdbc.datasource;

import io.nop.commons.util.ClassHelper;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public class SimpleDataSource implements DataSource {
    private String driverClassName;
    private String url;
    private String username;
    private String password;
    private String schema;
    private String catalog;
    private Properties properties;

    @Override
    public Connection getConnection() throws SQLException {
        return getConnection(getUsername(), getPassword());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Properties mergedProps = new Properties();
        Properties connProps = this.properties;
        if (connProps != null) {
            mergedProps.putAll(connProps);
        }
        if (username != null) {
            mergedProps.setProperty("user", username);
        }
        if (password != null) {
            mergedProps.setProperty("password", password);
        }

        Connection con = DriverManager.getConnection(url, mergedProps); // NOSONAR

        if (catalog != null)
            con.setCatalog(catalog);

        if (this.schema != null) {
            con.setSchema(this.schema);
        }
        return con;
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        throw new UnsupportedOperationException("getLogWriter");
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        throw new UnsupportedOperationException("setLogWriter");
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {

    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return 0;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return (T) this;
        }
        throw new SQLException("DataSource of type [" + getClass().getName() +
                "] cannot be unwrapped as [" + iface.getName() + "]");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this);
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName.trim();

        String driverClassNameToUse = driverClassName.trim();
        try {
            Class.forName(driverClassNameToUse, true, ClassHelper.getDefaultClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Could not load JDBC driver class [" + driverClassNameToUse + "]", ex);
        }
    }

    public String getCatalog() {
        return catalog;
    }

    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }
}
