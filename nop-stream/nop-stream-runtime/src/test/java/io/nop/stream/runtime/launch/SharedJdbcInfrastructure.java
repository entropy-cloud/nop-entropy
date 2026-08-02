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
 * connect to the same H2 {@code AUTO_SERVER=TRUE} database.
 *
 * <p>The {@code AUTO_SERVER=TRUE} H2 feature lets multiple JVMs on the same machine
 * share one file-based DB without a separate TCP server process — exactly what the
 * Stage 42 same-machine multi-JVM test needs.
 */
public final class SharedJdbcInfrastructure implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final IJdbcTemplate jdbcTemplate;

    public SharedJdbcInfrastructure(ClusterLaunchConfig config) {
        this.dataSource = new HikariDataSource();
        this.dataSource.setDriverClassName("org.h2.Driver");
        this.dataSource.setJdbcUrl(config.require(ClusterLaunchConfig.KEY_JDBC_URL));
        this.dataSource.setUsername(config.get(ClusterLaunchConfig.KEY_JDBC_USER, "sa"));
        this.dataSource.setPassword(config.get(ClusterLaunchConfig.KEY_JDBC_PASSWORD, ""));
        this.dataSource.setMaximumPoolSize(4);

        JdbcFactory factory = new JdbcFactory();
        this.jdbcTemplate = factory.newJdbcTemplate(factory.newTransactionTemplate(dataSource));
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
