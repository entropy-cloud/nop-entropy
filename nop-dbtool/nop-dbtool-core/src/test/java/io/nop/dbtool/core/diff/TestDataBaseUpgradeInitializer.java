package io.nop.dbtool.core.diff;

import java.util.List;
import javax.sql.DataSource;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.diff.DiffType;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.dialect.IDialect;
import io.nop.dbtool.core.DataBaseMeta;
import io.nop.dbtool.core.discovery.jdbc.JdbcMetaDiscovery;
import io.nop.dbtool.core.initialize.DataBaseUpgradeInitializer;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.factory.SessionFactoryImpl;
import io.nop.orm.factory.StaticOrmModelProvider;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.xdsl.DslModelHelper;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-04-29
 */
@NopTestConfig(localDb = true, initDatabaseSchema = true,
               testConfigFile = "classpath:/io/nop/dbtool/core/diff/db-diff-upgrade.yaml")
public class TestDataBaseUpgradeInitializer extends JunitBaseTestCase {
    private final static Logger LOG = LoggerFactory.getLogger(TestDataBaseUpgradeInitializer.class);

    @Inject
    protected DataSource dataSource;

    @Test
    public void test_upgrade() {
        // @PostConstruct 的 bean 不能采用注入方式，其在单元测试初始化时还未就绪
        DataBaseUpgradeInitializer initializer = BeanContainer.getBeanByType(DataBaseUpgradeInitializer.class);

        IResource resource = VirtualFileSystem.instance().getResource("/nop/db_diff/orm/new-app.orm.xml");
        OrmModel newOrmModel = (OrmModel) DslModelHelper.loadDslModel(resource);
        LOG.info("nop.test.orm.db-differ.upgrade.new-orm-mode.dump={}",
                 DslModelHelper.dslModelToXNode("/nop/schema/orm/orm.xdef", newOrmModel).xml());

        SessionFactoryImpl ormSessionFactory
                = (SessionFactoryImpl) BeanContainer.getBeanByType(IOrmSessionFactory.class);
        // 临时修改 ORM 模型，以验证数据库升级逻辑，不需要重新加载
        ormSessionFactory.setOrmModelHolder(new StaticOrmModelProvider(ormSessionFactory, newOrmModel));
        initializer.init(); // 执行升级

        // 确认数据库的升级是否符合预期
        JdbcMetaDiscovery discovery = JdbcMetaDiscovery.forDataSource(this.dataSource);
        IDialect dialect = discovery.getDialect();

        DataBaseMeta updatedDbMeta = discovery.discover(null, "PUBLIC", "DEV_%");
        List<OrmDbDiffer.DiffDdl> diffDdlList = OrmDbDiffer.forDialect(dialect).genDiffDdl(updatedDbMeta, newOrmModel);

        LOG.info("nop.test.orm.db-differ.upgrade.done.ddl={}", JsonTool.stringify(diffDdlList, null, "  "));
        // 只有字段或表的删除脚本未执行
        Assertions.assertFalse(diffDdlList.isEmpty());
        // CURRENT_TIMESTAMP的缺省值丢失
        Assertions.assertEquals(1,
                                diffDdlList.stream().filter(diffDdl -> diffDdl.getType() != DiffType.remove).count());
    }
}
