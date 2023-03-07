/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.quarkus.core.dao;

import java.time.Duration;
import java.util.Map;

public class DataSourceConfig {
    private String driverClassName;
    private String jdbcUrl;
    private String username;
    private String password;
    private int maxSize = 8;
    private int minSize;
    private int initialSize;
    private boolean metricsEnabled;
    private Duration acquisitionTimeout;
    private Duration backgroundValidationInterval;
    private Duration idleRemovalInterval;
    private Duration maxLifetime;

    private String validationQuerySql;

    private Map<String, String> properties;

    public Duration getIdleRemovalInterval() {
        return idleRemovalInterval;
    }

    public void setIdleRemovalInterval(Duration idleRemovalInterval) {
        this.idleRemovalInterval = idleRemovalInterval;
    }

    public Duration getMaxLifetime() {
        return maxLifetime;
    }

    public void setMaxLifetime(Duration maxLifetime) {
        this.maxLifetime = maxLifetime;
    }

    public Duration getBackgroundValidationInterval() {
        return backgroundValidationInterval;
    }

    public void setBackgroundValidationInterval(Duration backgroundValidationInterval) {
        this.backgroundValidationInterval = backgroundValidationInterval;
    }

    public String getValidationQuerySql() {
        return validationQuerySql;
    }

    public void setValidationQuerySql(String validationQuerySql) {
        this.validationQuerySql = validationQuerySql;
    }

    public int getMinSize() {
        return minSize;
    }

    public void setMinSize(int minSize) {
        this.minSize = minSize;
    }

    public int getInitialSize() {
        return initialSize;
    }

    public void setInitialSize(int initialSize) {
        this.initialSize = initialSize;
    }

    public Duration getAcquisitionTimeout() {
        return acquisitionTimeout;
    }

    public void setAcquisitionTimeout(Duration acquisitionTimeout) {
        this.acquisitionTimeout = acquisitionTimeout;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
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

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }
}
