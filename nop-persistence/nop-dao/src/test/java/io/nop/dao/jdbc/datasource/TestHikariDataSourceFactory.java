/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DataSourceConfig的生命周期/校验配置必须映射到HikariDataSource，
 * 静默丢弃会导致多数据源场景下连接长期滞留或失效连接不被检测
 */
public class TestHikariDataSourceFactory {

    private final HikariDataSourceFactory factory = new HikariDataSourceFactory();

    private static DataSourceConfig config() {
        DataSourceConfig config = new DataSourceConfig();
        config.setName("test-pool");
        config.setJdbcUrl("jdbc:h2:mem:testFactory");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        return config;
    }

    @Test
    public void testLifecycleConfigsAreMapped() {
        DataSourceConfig config = config();
        config.setIdleTimeout(Duration.ofSeconds(60));
        config.setMaxLifetime(Duration.ofMinutes(30));
        config.setValidationQuerySql("select 1");
        config.setBackgroundValidationInterval(Duration.ofMinutes(1));

        HikariDataSource ds = (HikariDataSource) factory.newDataSource(config);

        assertEquals(60_000L, ds.getIdleTimeout());
        assertEquals(1_800_000L, ds.getMaxLifetime());
        assertEquals("select 1", ds.getConnectionTestQuery());
        assertEquals(60_000L, ds.getKeepaliveTime());
    }

    @Test
    public void testKeepaliveNotAppliedWhenInvalid() {
        // backgroundValidationInterval不小于maxLifetime时，Hikari会拒绝启动，必须跳过该映射
        DataSourceConfig config = config();
        config.setMaxLifetime(Duration.ofMinutes(1));
        config.setBackgroundValidationInterval(Duration.ofMinutes(5));

        HikariDataSource ds = (HikariDataSource) factory.newDataSource(config);

        assertEquals(60_000L, ds.getMaxLifetime());
        assertEquals(120_000L, ds.getKeepaliveTime(),
                "keepaliveTime must stay at Hikari default when not less than maxLifetime");
    }

    @Test
    public void testDefaultsUnchangedWhenConfigOmitted() {
        HikariDataSource ds = (HikariDataSource) factory.newDataSource(config());

        assertEquals(120_000L, ds.getKeepaliveTime(), "Hikari default keepaliveTime");
        assertEquals(null, ds.getConnectionTestQuery());
    }
}
