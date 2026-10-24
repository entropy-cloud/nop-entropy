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
import java.sql.Statement;
import java.time.Duration;

@ConfigBean
public class JdbcConfig {
    /**
     * 2.2-C4-001（plan 2497 Phase 2）JDBC 超时两面的作用域裁定（m5）：超时为<b>连接级 + 语句级</b>，
     * 只作用于本配置源自身的连接与查询语句，不采用 {@code DriverManager.setLoginTimeout} 全局静态形态。
     * 理由：(1) setLoginTimeout 是 JVM 全局静态，波及同进程全部 DriverManager 连接（跨模块副作用）；
     * (2) login timeout 只覆盖建连阶段、不覆盖 socket 读挂起——审计指认的占死根因恰是 MySQL 缺省
     * socketTimeout=0 的无限读阻塞。默认有限超时使 DB 分区从「占死执行线程」退化为「周期性失败重试」。
     */

    /**
     * 连接建立超时（连接级，默认 10s）。经 JDBC URL 参数 {@code connectTimeout=&lt;毫秒&gt;} 传导
     * （MySQL Connector/J / MariaDB 毫秒语义；其他驱动参数名不同者，可显式配置 ≤0 停用追加，
     * 并在 jdbcUrl 中自行携带驱动参数）。显式配置 ≤0 = 停用该超时面。
     */
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * socket 读超时（连接级，默认 60s，须不小于 queryTimeout）。经 JDBC URL 参数
     * {@code socketTimeout=&lt;毫秒&gt;} 传导，语义同 {@link #DEFAULT_CONNECT_TIMEOUT}。
     */
    public static final Duration DEFAULT_SOCKET_TIMEOUT = Duration.ofSeconds(60);

    /**
     * 刷新查询超时（语句级，默认 60s）。经 {@link #applyQueryTimeout(Statement)} 传导到
     * {@link Statement#setQueryTimeout(int)}（秒粒度；正值不足 1s 时向上取整为 1s）。
     */
    public static final Duration DEFAULT_QUERY_TIMEOUT = Duration.ofSeconds(60);

    private String driverClassName;
    private String jdbcUrl;
    private String userName;
    private String password;

    private String selectAllQuery;

    private Duration refreshInterval;

    private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    private Duration socketTimeout = DEFAULT_SOCKET_TIMEOUT;
    private Duration queryTimeout = DEFAULT_QUERY_TIMEOUT;

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

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(Duration socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public Duration getQueryTimeout() {
        return queryTimeout;
    }

    public void setQueryTimeout(Duration queryTimeout) {
        this.queryTimeout = queryTimeout;
    }

    /**
     * 2.2-C4-001 超时面（语句级）：把刷新查询超时传导到 Statement（秒粒度）。
     * 超时未配置（null）或显式配置 ≤0 时不施加（显式停用面）；正值不足 1s 向上取整为 1s，
     * 避免亚秒配置被静默解释为「无超时」。
     */
    public void applyQueryTimeout(Statement statement) throws SQLException {
        if (statement == null || !isTimeoutEnabled(queryTimeout))
            return;
        long seconds = Math.max(1, queryTimeout.getSeconds());
        statement.setQueryTimeout((int) seconds);
    }

    /**
     * 2.2-C4-001 超时面（连接级）：向 jdbcUrl 追加 connectTimeout/socketTimeout 参数（毫秒，
     * MySQL Connector/J / MariaDB 参数名）。URL 已携带同名参数时不重复追加（URL 显式配置优先）；
     * 超时未配置（null）或显式配置 ≤0 时不追加该参数。包私有可见性供回归测试。
     */
    String buildConnectionUrl() {
        if (jdbcUrl == null)
            return null;
        StringBuilder sb = new StringBuilder(jdbcUrl);
        appendTimeoutParam(sb, "connectTimeout", connectTimeout);
        appendTimeoutParam(sb, "socketTimeout", socketTimeout);
        return sb.toString();
    }

    private void appendTimeoutParam(StringBuilder sb, String paramName, Duration timeout) {
        if (!isTimeoutEnabled(timeout))
            return;
        if (jdbcUrl.contains(paramName + "="))
            return;
        sb.append(sb.indexOf("?") >= 0 ? '&' : '?')
                .append(paramName).append('=').append(timeout.toMillis());
    }

    private static boolean isTimeoutEnabled(Duration timeout) {
        return timeout != null && !timeout.isZero() && !timeout.isNegative();
    }

    public Connection createConnection() throws SQLException {
        try {
            Class.forName(driverClassName);
            return DriverManager.getConnection(buildConnectionUrl(), userName, password);
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }
}