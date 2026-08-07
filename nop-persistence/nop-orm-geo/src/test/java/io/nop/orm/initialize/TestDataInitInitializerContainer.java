package io.nop.orm.initialize;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.impl.OrmTemplateImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 容器级验证：完整 IoC 容器（orm-defaults.beans.xml 装配）下，
 * DataInitInitializer 的 @PostConstruct init() 无需 ensureOrmTemplateSessionFactory 补丁
 * （a3fb3f620 已回退）。容器启动即创建 DataInitInitializer，若 ormTemplate.sessionFactory
 * 未由 IoC 装配完整，容器启动会直接失败。
 */
public class TestDataInitInitializerContainer extends BaseTestCase {

    @BeforeAll
    public static void initialize() {
        setTestConfig("nop.orm.init-database-schema", true);
        setTestConfig("nop.orm.init-database-data", true);
        setTestConfig("nop.orm.init-database-data-location", "/_test-init-data/");
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testDataInitWiredWithoutPatch() {
        // 容器已成功启动（DataInitInitializer 在启动阶段执行过 init()），
        // 且 ormTemplate 由 IoC 完整装配：sessionFactory 非空并与容器一致
        IOrmTemplate ormTemplate = BeanContainer.getBeanByType(IOrmTemplate.class);
        assertTrue(ormTemplate instanceof OrmTemplateImpl);
        IOrmSessionFactory sessionFactory = BeanContainer.getBeanByType(IOrmSessionFactory.class);
        assertSame(sessionFactory, ((OrmTemplateImpl) ormTemplate).getSessionFactory());

        // DataInitInitializer 加载的 CSV 数据已落库
        IDaoProvider daoProvider = BeanContainer.getBeanByType(IDaoProvider.class);
        IEntityDao<IOrmEntity> dao = daoProvider.daoForTable("test_index");
        List<IOrmEntity> all = dao.findAll();
        assertFalse(all.isEmpty());
        IOrmEntity entity = dao.getEntityById("idx-01");
        assertNotNull(entity);
        assertEquals("Index A", entity.orm_propValueByName("name"));
    }
}
