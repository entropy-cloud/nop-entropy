/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dbtool.core.diff;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.dbtool.core.DataBaseMeta;
import io.nop.dbtool.core.discovery.jdbc.JdbcMetaDiscovery;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.xdsl.DslModelHelper;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
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

    @Test
    public void test_genDiffDdl() {
        JdbcMetaDiscovery discovery = JdbcMetaDiscovery.forDataSource(this.dataSource);
        IDialect dialect = discovery.getDialect();

        // 初始化对比用的原始库
        OrmModel oldOrmModel = (OrmModel) DslModelHelper.loadDslModel(attachmentResource("db-diff-old.orm.xml"));
        initH2Db(dialect, oldOrmModel);

        OrmModel newOrmModel = (OrmModel) DslModelHelper.loadDslModel(attachmentResource("db-diff-new.orm.xml"));
        LOG.info("nop.test.orm.db-differ.new-orm-model.dump={}",
                 DslModelHelper.dslModelToXNode("/nop/schema/orm/orm.xdef", newOrmModel).xml());

        // H2 的数据库名为其文件名，且表名和数据库名在 Linux 中都默认为大写，可以在 url 中附加配置 `;CASE_INSENSITIVE_IDENTIFIERS=TRUE` 以配置大小写无关
        // H2 的 table schema 分为 PUBLIC 和 INFORMATION_SCHEMA 两种，前者为用户创建的表，后者为数据库内部表
        // 获取 H2 表信息: SELECT * FROM INFORMATION_SCHEMA.TABLES
        String catalog = discovery.getCatalogs().get(0);
        DataBaseMeta oldDbMeta = discovery.discover(catalog, "PUBLIC", "DEV_%");

        List<OrmDbDiffer.DiffDdl> diffDdlList = OrmDbDiffer.forDialect(dialect).genDiffDdl(oldDbMeta, newOrmModel);
        String resultsJson = JsonTool.stringify(diffDdlList, null, "  ");

        LOG.info("nop.test.orm.db-differ.ddl={}", resultsJson);
        Assertions.assertEquals(normalizeJsonString(attachmentJsonText("db-diff-results.json")), normalizeJsonString(resultsJson));

        // 根据生成的 DDL 脚本更新原始库
        updateH2Db(diffDdlList);

        // 再检查更新原始库后是否还存在差异
        DataBaseMeta updatedDbMeta = discovery.discover(catalog, "PUBLIC", "DEV_%");
        diffDdlList = OrmDbDiffer.forDialect(dialect).genDiffDdl(updatedDbMeta, newOrmModel);

        LOG.info("nop.test.orm.db-differ.ddl={}", JsonTool.stringify(diffDdlList, null, "  "));
        Assertions.assertEquals(1, diffDdlList.size());
    }

    @Test
    public void test_genDiffDdl_by_Entities() {
        OrmModel oldOrmModel = (OrmModel) DslModelHelper.loadDslModel(attachmentResource("db-diff-old.orm.xml"));
        OrmModel newOrmModel = (OrmModel) DslModelHelper.loadDslModel(attachmentResource("db-diff-new.orm.xml"));

        List<OrmDbDiffer.DiffDdl> diffDdlList = OrmDbDiffer.forDialect("h2")
                                                           .genDiffDdl(oldOrmModel.getEntities(),
                                                                       newOrmModel.getEntities());
        String resultsJson = JsonTool.stringify(diffDdlList, null, "  ");

        LOG.info("nop.test.orm.db-differ.by-entities.ddl={}", resultsJson);
        Assertions.assertEquals(normalizeJsonString(attachmentJsonText("db-diff-results2.json")), normalizeJsonString(resultsJson));

        diffDdlList = OrmDbDiffer.forDialect("h2").genDiffDdl(null, newOrmModel.getEntities());
        resultsJson = JsonTool.stringify(diffDdlList, null, "  ");

        LOG.info("nop.test.orm.db-differ.by-entities.old-is-null.ddl={}", resultsJson);
        Assertions.assertEquals(normalizeJsonString(attachmentJsonText("db-diff-results.old-null-entities.json")), normalizeJsonString(resultsJson));

        diffDdlList = OrmDbDiffer.forDialect("h2").genDiffDdl(oldOrmModel.getEntities(), new ArrayList<>());
        resultsJson = JsonTool.stringify(diffDdlList, null, "  ");

        LOG.info("nop.test.orm.db-differ.by-entities.new-is-null.ddl={}", resultsJson);
        Assertions.assertEquals(attachmentJsonText("db-diff-results.new-null-entities.json"), resultsJson);
    }

    private void initH2Db(IDialect dialect, OrmModel ormModel) {
        String sql = DdlSqlCreator.forDialect(dialect).createTables(ormModel.getEntities(), true);
        LOG.info("nop.test.orm.db-differ.h2.init.sql={}", sql);

        createJdbcTemplate().executeMultiSql(new SQL(sql));
    }

    private void updateH2Db(List<OrmDbDiffer.DiffDdl> diffDdlList) {
        String sql = OrmDbDiffer.DiffDdl.toSql(diffDdlList);
        LOG.info("nop.test.orm.db-differ.h2.update.sql={}", sql);

        createJdbcTemplate().executeMultiSql(new SQL(sql));
    }

    private IJdbcTemplate createJdbcTemplate() {
        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate transactionTemplate = factory.newTransactionTemplate(this.dataSource);

        return factory.newJdbcTemplate(transactionTemplate);
    }
}
