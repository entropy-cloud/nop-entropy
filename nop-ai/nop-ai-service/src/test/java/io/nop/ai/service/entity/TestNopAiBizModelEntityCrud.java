package io.nop.ai.service.entity;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.nop.ai.dao.entity.NopAiSession;
import io.nop.ai.dao.entity.NopAiSessionMessage;
import io.nop.ai.dao.entity.NopAiTodo;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.seq.UuidSequenceGenerator;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.dao.OrmDaoProvider;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.factory.DefaultOrmColumnBinderEnhancer;
import io.nop.orm.factory.OrmSessionFactoryBean;
import io.nop.orm.impl.OrmTemplateImpl;
import io.nop.orm.model.IEntityModel;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * MA4.3-03/-07 focused tests: CRUD contract for the entities behind three
 * nop-ai-service BizModels (NopAiSessionBizModel / NopAiTodoBizModel /
 * NopAiSessionMessageBizModel), following the H2-backed pattern of
 * {@link TestNopAiChatResponseSummarizeByModel}.
 */
public class TestNopAiBizModelEntityCrud {

    private SimpleDataSource dataSource;
    private IJdbcTemplate jdbcTemplate;
    private OrmSessionFactoryBean factoryBean;
    private IOrmTemplate orm;
    private IDaoProvider daoProvider;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:svc-orm-test-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new IBeanProvider() {
            @Override
            public boolean containsBean(String name) {
                return false;
            }

            @Override
            public <T> T getBeanByType(Class<T> clazz) {
                try {
                    return clazz.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("cannot instantiate " + clazz.getName(), e);
                }
            }

            @Override
            public Object getBean(String name) {
                return null;
            }

            @Override
            public String getBeanScope(String name) {
                return null;
            }
        });
        factoryBean.setGlobalCache(new LocalCacheProvider("svc-orm-test", CacheConfig.newConfig(100)));
        factoryBean.setSequenceGenerator(new UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
        factoryBean.init();

        IOrmSessionFactory sessionFactory = factoryBean.getObject();
        orm = new OrmTemplateImpl(sessionFactory);
        daoProvider = new OrmDaoProvider(orm);

        Collection<? extends IEntityModel> tables = sessionFactory.getOrmModel().getEntityModelsInTopoOrder();
        String createSql = new DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null)).createTables(tables, false);
        jdbcTemplate.executeMultiSql(new SQL(createSql));
    }

    @AfterEach
    void tearDown() {
        if (factoryBean != null)
            factoryBean.destroy();
    }

    @Test
    public void testNopAiSessionBizModelEntityCrud() {
        IEntityDao<NopAiSession> dao = daoProvider.daoFor(NopAiSession.class);

        NopAiSession session = dao.newEntity();
        session.setId("svc-s1");
        session.setProjectId("p-1");
        session.setAgentName("agent-svc");
        session.setSlug("slug-svc");
        session.setStatus(1);
        dao.saveEntity(session);

        NopAiSession loaded = dao.requireEntityById("svc-s1");
        assertEquals("agent-svc", loaded.getAgentName());
        assertNotNull(loaded.getCreateTime());

        orm.runInSession(s -> {
            NopAiSession updatable = dao.requireEntityById("svc-s1");
            updatable.setStatus(2);
            dao.updateEntity(updatable);
            return null;
        });
        assertEquals(2, dao.requireEntityById("svc-s1").getStatus().intValue());

        orm.runInSession(s -> {
            dao.deleteEntity(dao.requireEntityById("svc-s1"));
            return null;
        });
        Long count = jdbcTemplate.findFirst(
                SQL.begin().sql("select count(*) from nop_ai_session where id = 'svc-s1'").end());
        assertEquals(0L, count);
    }

    @Test
    public void testNopAiTodoBizModelEntityCrudWithStatusTransition() {
        IEntityDao<NopAiTodo> dao = daoProvider.daoFor(NopAiTodo.class);

        NopAiTodo todo = dao.newEntity();
        todo.setId("svc-t1");
        todo.setSessionId("svc-s1");
        todo.setContent("review code");
        todo.setStatus(0);
        todo.setPriority(2);
        todo.setPosition(1);
        todo.setVersion(1);
        dao.saveEntity(todo);

        assertEquals("review code", dao.requireEntityById("svc-t1").getContent());

        orm.runInSession(s -> {
            NopAiTodo updatable = dao.requireEntityById("svc-t1");
            updatable.setStatus(1);
            dao.updateEntity(updatable);
            return null;
        });
        NopAiTodo done = dao.requireEntityById("svc-t1");
        assertEquals(1, done.getStatus().intValue());
        assertEquals(2, done.getPriority().intValue());
    }

    @Test
    public void testNopAiSessionMessageBizModelEntityCrudWithSessionRelation() {
        IEntityDao<NopAiSession> sessionDao = daoProvider.daoFor(NopAiSession.class);
        NopAiSession session = sessionDao.newEntity();
        session.setId("svc-s2");
        session.setProjectId("p-1");
        session.setAgentName("agent-msg");
        session.setSlug("slug-msg");
        session.setStatus(1);
        sessionDao.saveEntity(session);

        IEntityDao<NopAiSessionMessage> msgDao = daoProvider.daoFor(NopAiSessionMessage.class);
        for (int i = 1; i <= 3; i++) {
            NopAiSessionMessage msg = msgDao.newEntity();
            msg.setId("svc-msg-" + i);
            msg.setSessionId("svc-s2");
            msg.setRole(0);
            msg.setSeq((long) i);
            msg.setContent("step-" + i);
            msg.setVersion(1);
            msgDao.saveEntity(msg);
        }

        Long messageCount = jdbcTemplate.findFirst(SQL.begin()
                .sql("select count(*) from nop_ai_session_message where session_id = 'svc-s2'").end());
        assertEquals(3L, messageCount, "session relation must persist all child messages");

        NopAiSessionMessage first = msgDao.requireEntityById("svc-msg-1");
        assertEquals("svc-s2", first.getSessionId());
        assertEquals("step-1", first.getContent());
    }

    @Test
    public void testBizModelEntityWiring() {
        assertEquals(NopAiSession.class.getName(), new NopAiSessionBizModel().getEntityName());
        assertEquals(NopAiTodo.class.getName(), new NopAiTodoBizModel().getEntityName());
        assertEquals(NopAiSessionMessage.class.getName(), new NopAiSessionMessageBizModel().getEntityName());
    }
}
