/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dialect;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.api.core.config.AppConfig;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.dao.TestJsonComponent;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.junit.jupiter.Testcontainers;

@EnabledIfSystemProperty(named = "nop.test.docker.enabled", matches = "true")
@Testcontainers
public class TestPostgreSqlJsonComponent extends TestJsonComponent {
    protected HikariDataSource createDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setMetricRegistry(GlobalMeterRegistry.instance());
        ds.setDriverClassName("org.testcontainers.jdbc.ContainerDatabaseDriver");
        // String timeZone = ZoneId.systemDefault().getId();
        // 固定 16-alpine：本地镜像可用，避免 latest 拉取超时（ContainerFetchException 2min）
        ds.setJdbcUrl("jdbc:tc:postgresql:16-alpine:///test?TC_DAEMON=true");
        ds.setUsername(AppConfig.var("nop.database.username", "test"));
        ds.setPassword(AppConfig.var("nop.database.password", "test"));
        ds.setMaximumPoolSize(2);
        return ds;
    }

    /** 基类 /init.sql 是 H2 方言，PostgreSQL 上语法不兼容；所需表由 ORM 模型建出 */
    @Override
    protected void executeSqlFile(String sqlFile) {
    }

    /**
     * 共享模型全量 DDL 含 PG 方言不支持的 VARBINARY 列（std-data-type-not-supported）；
     * 本测试只需 sims_college/sims_class，单独建表。TC_DAEMON 复用容器时先 drop 再建。
     */
    @Override
    protected void createTables() {
        IEntityModel collegeModel = sessionFactory.getOrmModel().requireEntityModel("io.nop.app.SimsCollege");
        IEntityModel classModel = sessionFactory.getOrmModel().requireEntityModel("io.nop.app.SimsClass");
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("drop table if exists " + collegeModel.getTableName().toLowerCase()).end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("drop table if exists " + classModel.getTableName().toLowerCase()).end());
        DdlSqlCreator creator = DdlSqlCreator.forDialect(jdbcTemplate.getDialectForQuerySpace(null));
        jdbcTemplate.executeUpdate(new SQL(creator.createTable(collegeModel)));
        jdbcTemplate.executeUpdate(new SQL(creator.createTable(classModel)));
    }
}
