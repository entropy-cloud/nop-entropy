/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.source.jdbc;

import io.nop.api.core.annotations.config.ConfigBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.config.source.ConfigSourceHelper;
import io.nop.config.source.IConfigSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;

@ConfigBean
public class JdbcConfig {
    private String driverClassName;
    private String jdbcUrl;
    private String userName;
    private String password;

    private String selectAllQuery;

    private Duration refreshInterval;

    public static JdbcConfig create(String prefix, IConfigSource config) {
        JdbcConfig configBean = new JdbcConfig();
        ConfigSourceHelper.initConfigBean(configBean, config, prefix);
        return configBean;
    }

    public boolean valid() {
        return !StringHelper.isEmpty(driverClassName) && !StringHelper.isEmpty(jdbcUrl)
                && !StringHelper.isEmpty(selectAllQuery);
    }

    public String getSelectAllQuery() {
        return selectAllQuery;
    }

    public void setSelectAllQuery(String selectAllQuery) {
        this.selectAllQuery = selectAllQuery;
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

    public Duration getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(Duration refreshInterval) {
        this.refreshInterval = refreshInterval;
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

    public Connection createConnection() throws SQLException {
        try {
            Class.forName(driverClassName);
            return DriverManager.getConnection(jdbcUrl, userName, password);
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }
}