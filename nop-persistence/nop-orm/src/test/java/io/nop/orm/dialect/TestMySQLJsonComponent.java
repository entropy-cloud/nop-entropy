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

import java.time.ZoneId;

@EnabledIfSystemProperty(named = "nop.test.docker.enabled", matches = "true")
@Testcontainers
public class TestMySQLJsonComponent extends TestJsonComponent {
    protected HikariDataSource createDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setMetricRegistry(GlobalMeterRegistry.instance());
        ds.setDriverClassName("org.testcontainers.jdbc.ContainerDatabaseDriver");
        String timeZone = ZoneId.systemDefault().getId();
        // 通过TC_MY_CNF覆盖了mysql.ini文件，增大max_packet_size
        ds.setJdbcUrl("jdbc:tc:mysql:8.0:///test?TC_DAEMON=true&TC_TMPFS=/testtmpfs:rw&TC_MY_CNF=docker/mysql_conf"
                + "&serverTimezone=" + timeZone + "&useUnicode=true&characterEncoding=utf-8&useSSL=false");
        ds.setUsername(AppConfig.var("nop.database.username", "test"));
        ds.setPassword(AppConfig.var("nop.database.password", "test"));
        ds.setMaximumPoolSize(2);
        return ds;
    }

    /**
     * 基类 /init.sql 是 H2 方言且 TC_DAEMON 复用容器时跨用例重复建表（my_entity 已存在）；
     * 本测试所需表由下方 createTables 从 ORM 模型建出。
     */
    @Override
    protected void executeSqlFile(String sqlFile) {
    }

    /**
     * 共享模型全量 DDL 含 MySQL 8 不兼容的可空主键修订表（All parts of a PRIMARY KEY must
     * be NOT NULL）；本测试只需 sims_college/sims_class，单独建 MySQL 兼容表。
     * TC_DAEMON 复用容器时先 drop 再建。
     */
    @Override
    protected void createTables() {
        IEntityModel collegeModel = sessionFactory.getOrmModel().requireEntityModel("io.nop.app.SimsCollege");
        IEntityModel classModel = sessionFactory.getOrmModel().requireEntityModel("io.nop.app.SimsClass");
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("drop table if exists " + collegeModel.getTableName()).end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("drop table if exists " + classModel.getTableName()).end());
        DdlSqlCreator creator = DdlSqlCreator.forDialect(jdbcTemplate.getDialectForQuerySpace(null));
        jdbcTemplate.executeUpdate(new SQL(creator.createTable(collegeModel)));
        jdbcTemplate.executeUpdate(new SQL(creator.createTable(classModel)));
    }
}
