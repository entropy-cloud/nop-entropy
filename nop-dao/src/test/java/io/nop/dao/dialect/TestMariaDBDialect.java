/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.dialect;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.api.core.config.AppConfig;
import io.nop.commons.metrics.GlobalMeterRegistry;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.ZoneId;

@EnabledIfSystemProperty(named = "nop.test.docker.enabled", matches = "true")
@Testcontainers
public class TestMariaDBDialect extends TestDialect {
    protected HikariDataSource createDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setMetricRegistry(GlobalMeterRegistry.instance());
        ds.setDriverClassName("org.testcontainers.jdbc.ContainerDatabaseDriver");
        String timeZone = ZoneId.systemDefault().getId();
        ds.setJdbcUrl("jdbc:tc:mariadb:///test?TC_DAEMON=true&TC_TMPFS=/testtmpfs:rw&TC_MY_CNF=docker/mysql_conf"
                + "&serverTimezone=" + timeZone + "&useUnicode=true&characterEncoding=utf-8&useSSL=false");
        ds.setUsername(AppConfig.var("nop.database.username", "test"));
        ds.setPassword(AppConfig.var("nop.database.password", "test"));
        ds.setMaximumPoolSize(maxPoolSize);
        return ds;
    }

}
