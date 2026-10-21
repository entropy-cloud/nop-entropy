/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dao;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.commons.metrics.GlobalMeterRegistry;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 方案 B 过滤参数绑定回归——PostgreSQL（真实缺陷现场）。
 *
 * <p>PostgreSQL 无 bigint = varchar 隐式算子：修复前所有带 BIGINT+string 列过滤的
 * QueryBean/EQL 查询报 42883（operator does not exist），修复后参数按列 SQL 类型
 * 绑成 Long，全部用例可执行。</p>
 *
 * <p>开启方式：{@code -Dnop.test.docker.enabled=true}。不显式传该参数则本类被跳过。</p>
 */
@EnabledIfSystemProperty(named = "nop.test.docker.enabled", matches = "true")
@Testcontainers
public class TestBigIntStringFilterPostgres extends AbstractBigIntStringFilterCase {

    @Override
    protected HikariDataSource createDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setMetricRegistry(GlobalMeterRegistry.instance());
        ds.setDriverClassName("org.testcontainers.jdbc.ContainerDatabaseDriver");
        ds.setJdbcUrl("jdbc:tc:postgresql:16-alpine:///test?TC_DAEMON=true");
        ds.setUsername("test");
        ds.setPassword("test");
        ds.setMaximumPoolSize(2);
        return ds;
    }
}
