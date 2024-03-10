/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.app.SimsClass;
import io.nop.app.SimsCollege;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.seq.UuidSequenceGenerator;
import io.nop.orm.dao.OrmDaoProvider;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.factory.DefaultOrmColumnBinderEnhancer;
import io.nop.orm.factory.OrmSessionFactoryBean;
import io.nop.orm.impl.OrmTemplateImpl;
import io.nop.orm.mock.MockBeanProvider;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AbstractOrmTestCase extends AbstractJdbcTestCase {
    protected IOrmSessionFactory sessionFactory;
    private IOrmTemplate ormTemplate;
    private IDaoProvider daoProvider;

    private OrmSessionFactoryBean sessionFactoryBean;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        OrmSessionFactoryBean factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MockBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("global", CacheConfig.newConfig(1000)));
        factoryBean.setSequenceGenerator(new UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());

        factoryBean.init();

        this.sessionFactoryBean = factoryBean;

        sessionFactory = factoryBean.getObject();
        ormTemplate = new OrmTemplateImpl(sessionFactory);
        daoProvider = new OrmDaoProvider(ormTemplate);

        sqlLibManager.setOrmTemplate(orm());

        IContext context = ContextProvider.instance().getOrCreateContext();
        context.setTenantId("123");
        context.setUserId("456");

        assertEquals("123", ContextProvider.currentTenantId());

        createTables();
        prepareData();

        prepareDataInTenant("100");
    }

    @AfterEach
    public void tearDown() {
        super.tearDown();
        if (sessionFactoryBean != null)
            sessionFactoryBean.destroy();
    }

    protected void createTables() {
        Collection<IEntityModel> tables = sessionFactory.getOrmModel().getEntityModelsInTopoOrder();
        String createSql = new DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null)).createTables(tables, false);
        jdbcTemplate.executeMultiSql(new SQL(createSql));
    }

    protected void prepareData() {
        IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
        SimsCollege entity = dao.newEntity();
        entity.setCollegeId("1");
        entity.setCollegeName("CollegeA");
        entity.setIntro("intro");
        entity.setShortName("A");
        dao.saveEntity(entity);

        SimsClass classEntity = daoProvider().daoFor(SimsClass.class).newEntity();
        classEntity.setClassId("11");
        classEntity.setCollegeId("1");
        classEntity.setClassName("classA");
        classEntity.setMajorId("a");
        orm().save(classEntity);
    }

    protected void prepareDataInTenant(String tenantId) {
        String oldTenantId = ContextProvider.currentTenantId();
        ContextProvider.getOrCreateContext().setTenantId(tenantId);

        IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
        SimsCollege entity = dao.newEntity();
        entity.setCollegeId("-1");
        entity.setCollegeName("-CollegeA");
        entity.setIntro("-intro");
        entity.setShortName("-A");
        dao.saveEntity(entity);

        SimsClass classEntity = daoProvider().daoFor(SimsClass.class).newEntity();
        classEntity.setClassId("-11");
        classEntity.setCollegeId("-1");
        classEntity.setClassName("-classA");
        classEntity.setMajorId("-a");
        orm().save(classEntity);

        ContextProvider.getOrCreateContext().setTenantId(oldTenantId);
    }

    protected IOrmTemplate orm() {
        return ormTemplate;
    }

    protected IDaoProvider daoProvider() {
        return daoProvider;
    }

    protected List<Integer> insertColleges(int min, int max) {
        List<Integer> ids = new ArrayList<>();
        IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);

        orm().runInSession(() -> {
            for (int i = min; i <= max; i++) {
                SimsCollege entity = dao.newEntity();
                ids.add(i);
                entity.setCollegeId(i + "");
                entity.setCollegeName("College" + i);
                entity.setIntro("intro" + i);
                entity.setShortName("N" + i);
                dao.saveEntity(entity);
            }
        });
        return ids;
    }

    protected List<Integer> insertClass(int min, int max) {
        List<Integer> ids = new ArrayList<>();
        IEntityDao<SimsClass> dao = daoProvider().daoFor(SimsClass.class);

        orm().runInSession(() -> {
            for (int i = min; i < max; i++) {
                SimsClass entity = dao.newEntity();
                ids.add(i);
                entity.setCollegeId(i + "");
                // entity.setCollegeName("College" + i);
                entity.setClassId(i + "");
                entity.setClassName("Class" + i);
                dao.saveEntity(entity);
            }
        });
        return ids;
    }
}
