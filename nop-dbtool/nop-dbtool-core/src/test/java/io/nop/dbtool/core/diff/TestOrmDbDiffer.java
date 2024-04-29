/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dbtool.core.diff;

import java.util.List;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.dbtool.core.DataBaseMeta;
import io.nop.dbtool.core.discovery.jdbc.JdbcMetaDiscovery;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.xdsl.DslModelHelper;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-04-27
 */
@NopTestConfig(localDb = true)
public class TestOrmDbDiffer extends JunitBaseTestCase {
    private final static Logger LOG = LoggerFactory.getLogger(TestOrmDbDiffer.class);

    @Inject
    protected DataSource dataSource;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void test_genDiffDdl() {
        // 初始化对比用的原始库
        OrmModel oldOrmModel = (OrmModel) DslModelHelper.loadDslModel(attachmentResource("db-diff-old.orm.xml"));
        initH2Db(oldOrmModel);

        OrmModel newOrmModel = (OrmModel) DslModelHelper.loadDslModel(attachmentResource("db-diff-new.orm.xml"));

        JdbcMetaDiscovery discovery = new JdbcMetaDiscovery();
        // H2 的数据库名为其文件名，且表名和数据库名在 Linux 中都默认为大写，可以在 url 中附加配置 `;CASE_INSENSITIVE_IDENTIFIERS=TRUE` 以配置大小写无关
        // H2 的 table schema 分为 PUBLIC 和 INFORMATION_SCHEMA 两种，前者为用户创建的表，后者为数据库内部表
        // 获取 H2 表信息: SELECT * FROM INFORMATION_SCHEMA.TABLES
        String catalog = discovery.getCatalogs(this.dataSource).get(0);
        DataBaseMeta oldDbMeta = discovery.discover(this.dataSource, catalog, "PUBLIC", "DEV_%");

        List<OrmDbDiffer.ResultLine> results = OrmDbDiffer.forDialect("h2").genDiffDdl(oldDbMeta, newOrmModel);
        String resultsJson = JsonTool.stringify(results, null, "  ");

        LOG.info("nop.test.orm-db.differ.ddl={}", resultsJson);
        Assertions.assertEquals(attachmentJsonText("db-diff-results.json"), resultsJson);

        // 根据生成的 DDL 脚本更新原始库
        updateH2Db(results);

        // 再检查更新原始库后是否还存在差异
        DataBaseMeta updatedDbMeta = discovery.discover(this.dataSource, catalog, "PUBLIC", "DEV_%");
        results = OrmDbDiffer.forDialect("h2").genDiffDdl(updatedDbMeta, newOrmModel);

        LOG.info("nop.test.orm-db.differ.ddl={}", JsonTool.stringify(results, null, "  "));
        Assertions.assertTrue(results.isEmpty());
    }

    private void initH2Db(OrmModel ormModel) {
        String sql = DdlSqlCreator.forDialect("h2").createTables(ormModel.getEntities(), true);
        LOG.info("nop.test.orm-db.differ.h2.init.sql={}", sql);

        createJdbcTemplate().executeMultiSql(new SQL(sql));
    }

    private void updateH2Db(List<OrmDbDiffer.ResultLine> ddlLines) {
        String sql = ddlLines.stream().map(OrmDbDiffer.ResultLine::getLine).collect(Collectors.joining(";"));
        LOG.info("nop.test.orm-db.differ.h2.update.sql={}", sql);

        createJdbcTemplate().executeMultiSql(new SQL(sql));
    }

    private IJdbcTemplate createJdbcTemplate() {
        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate transactionTemplate = factory.newTransactionTemplate(this.dataSource);

        return factory.newJdbcTemplate(transactionTemplate);
    }
}
