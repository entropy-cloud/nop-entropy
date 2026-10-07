/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.launch;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;

/**
 * Stage 42 Phase 1: builds the shared JDBC infrastructure (one
 * {@link DataSource} + one {@link IJdbcTemplate}) from a {@link ClusterLaunchConfig}.
 * Both {@link TaskManagerMain} and {@link JobCoordinatorMain} use this so they
 * connect to the same backing database.
 *
 * <p>The historical default driver is H2 in {@code AUTO_SERVER=TRUE} mode (file-based,
 * multi-process accessible on the same machine), which lets multiple JVMs on one
 * machine share a file-based DB without a separate TCP server process. WI1 (plan
 * 2328) made the driver configurable via {@code jdbcDriver} for production launches
 * against MySQL/PostgreSQL — the driver class is loaded by Hikari at pool start and
 * fails fast when the driver jar is absent from the process classpath.
 */
public final class SharedJdbcInfrastructure implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final IJdbcTemplate jdbcTemplate;

    public SharedJdbcInfrastructure(ClusterLaunchConfig config) {
        this.dataSource = buildDataSource(config);

        JdbcFactory factory = new JdbcFactory();
        this.jdbcTemplate = factory.newJdbcTemplate(factory.newTransactionTemplate(dataSource));
    }

    /**
     * Builds the Hikari pool configuration from the launch config. Package-private
     * static so the wiring test can pin the {@code jdbcDriver} forwarding without
     * starting a pool (which would probe a connection and drag in dialect
     * resolution — unavailable in a capped-initialization test JVM).
     */
    static HikariDataSource buildDataSource(ClusterLaunchConfig config) {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName(config.get(ClusterLaunchConfig.KEY_JDBC_DRIVER, "org.h2.Driver"));
        ds.setJdbcUrl(config.require(ClusterLaunchConfig.KEY_JDBC_URL));
        ds.setUsername(config.get(ClusterLaunchConfig.KEY_JDBC_USER, "sa"));
        ds.setPassword(config.get(ClusterLaunchConfig.KEY_JDBC_PASSWORD, ""));
        ds.setMaximumPoolSize(4);
        return ds;
    }

    public SharedJdbcInfrastructure(HikariDataSource dataSource, IJdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    public IJdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
