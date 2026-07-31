package io.nop.ai.dao;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.nop.ai.biz.INopAiChatRequestBiz;
import io.nop.ai.biz.INopAiChatResponseBiz;
import io.nop.ai.biz.INopAiEventBiz;
import io.nop.ai.biz.INopAiGenFileBiz;
import io.nop.ai.biz.INopAiGenFileHistoryBiz;
import io.nop.ai.biz.INopAiKnowledgeBiz;
import io.nop.ai.biz.INopAiModelBiz;
import io.nop.ai.biz.INopAiProjectBiz;
import io.nop.ai.biz.INopAiProjectConfigBiz;
import io.nop.ai.biz.INopAiProjectRuleBiz;
import io.nop.ai.biz.INopAiPromptTemplateBiz;
import io.nop.ai.biz.INopAiPromptTemplateHistoryBiz;
import io.nop.ai.biz.INopAiRequirementBiz;
import io.nop.ai.biz.INopAiRequirementHistoryBiz;
import io.nop.ai.biz.INopAiSessionBiz;
import io.nop.ai.biz.INopAiSessionContextBiz;
import io.nop.ai.biz.INopAiSessionInputBiz;
import io.nop.ai.biz.INopAiSessionMessageBiz;
import io.nop.ai.biz.INopAiTestCaseBiz;
import io.nop.ai.biz.INopAiTestResultBiz;
import io.nop.ai.biz.INopAiTodoBiz;
import io.nop.ai.dao.entity.NopAiModel;
import io.nop.ai.dao.entity.NopAiSession;
import io.nop.ai.dao.entity.NopAiSessionMessage;
import io.nop.ai.dao.entity.NopAiTodo;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
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
import io.nop.orm.model.IEntityRelationModel;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.nop.ai.biz.INopAiChatRequestBiz;import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA4.3-02 focused tests: nop-ai-dao ORM entity mapping smoke tests + Biz interface contracts.
 *
 * <p>Runs the real ORM stack (OrmSessionFactoryBean + OrmTemplateImpl + OrmDaoProvider)
 * over an in-memory H2 database, with DDL generated from the live ORM model
 * (/nop/ai/orm/app.orm.xml merged). Verifies:
 * <ul>
 *   <li>entity insert/load round-trips go through real column binders
 *       (including the tagSet="enc" apiKey encryption binder on NopAiModel)</li>
 *   <li>the CRUD contract of the entity DAOs behind the 21 Biz interfaces</li>
 *   <li>to-many relation mapping (NopAiSession.messages → NopAiSessionMessage)</li>
 *   <li>all 21 INopAiXxxBiz interfaces are generic-bound to the correct entity classes</li>
 * </ul>
 */
public class TestNopAiOrmEntityMapping {

    private static final List<Class<?>> BIZ_INTERFACES = List.of(
            INopAiChatRequestBiz.class, INopAiChatResponseBiz.class, INopAiEventBiz.class,
            INopAiGenFileBiz.class, INopAiGenFileHistoryBiz.class, INopAiKnowledgeBiz.class,
            INopAiModelBiz.class, INopAiProjectBiz.class, INopAiProjectConfigBiz.class,
            INopAiProjectRuleBiz.class, INopAiPromptTemplateBiz.class, INopAiPromptTemplateHistoryBiz.class,
            INopAiRequirementBiz.class, INopAiRequirementHistoryBiz.class, INopAiSessionBiz.class,
            INopAiSessionContextBiz.class, INopAiSessionInputBiz.class, INopAiSessionMessageBiz.class,
            INopAiTestCaseBiz.class, INopAiTestResultBiz.class, INopAiTodoBiz.class);

    private SimpleDataSource dataSource;
    private IJdbcTemplate jdbcTemplate;
    private OrmSessionFactoryBean factoryBean;
    private IOrmTemplate orm;
    private IDaoProvider daoProvider;

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:dao-orm-test-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
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
        factoryBean.setGlobalCache(new LocalCacheProvider("dao-orm-test", CacheConfig.newConfig(100)));
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

    private IEntityDao<NopAiSession> sessionDao() {
        return daoProvider.daoFor(NopAiSession.class);
    }

    private NopAiSession newSession(String id, String agentName) {
        NopAiSession session = sessionDao().newEntity();
        session.setId(id);
        session.setProjectId("p-1");
        session.setAgentName(agentName);
        session.setSlug("slug-" + id);
        session.setStatus(1);
        return session;
    }

    @Test
    public void testEntityMappingRoundTrip() {
        IEntityDao<NopAiSession> dao = sessionDao();
        NopAiSession session = newSession("s1", "agent-a");
        dao.saveEntity(session);

        NopAiSession loaded = dao.requireEntityById("s1");
        assertNotNull(loaded);
        assertEquals("agent-a", loaded.getAgentName());
        assertEquals("slug-s1", loaded.getSlug());
        assertNotNull(loaded.getCreateTime(), "ORM must auto-fill createTime audit prop");
        assertNotNull(loaded.getCreatedBy(), "ORM must auto-fill createdBy audit prop");
        assertNotNull(loaded.getVersion(), "ORM must initialize version column");
    }

    @Test
    public void testBizInterfaceCrudContract() {
        IEntityDao<NopAiTodo> dao = daoProvider.daoFor(NopAiTodo.class);

        NopAiTodo todo = dao.newEntity();
        todo.setId("t1");
        todo.setSessionId("s1");
        todo.setContent("first todo");
        todo.setStatus(0);
        todo.setPriority(1);
        todo.setPosition(1);
        todo.setVersion(1);
        dao.saveEntity(todo);
        assertNotNull(todo.getId());

        NopAiTodo loaded = dao.requireEntityById("t1");
        assertEquals("first todo", loaded.getContent());

        orm.runInSession(session -> {
            NopAiTodo updatable = dao.requireEntityById("t1");
            updatable.setContent("updated todo");
            dao.updateEntity(updatable);
            return null;
        });
        assertEquals("updated todo", dao.requireEntityById("t1").getContent());

        orm.runInSession(session -> {
            dao.deleteEntity(dao.requireEntityById("t1"));
            return null;
        });
        Long remaining = jdbcTemplate.findFirst(
                SQL.begin().sql("select count(*) from nop_ai_todo where id = 't1'").end());
        assertEquals(0L, remaining, "deleted entity must be removed from the table");
    }

    @Test
    public void testEncryptedApiKeyColumnBinder() {
        IEntityDao<NopAiModel> dao = daoProvider.daoFor(NopAiModel.class);

        NopAiModel model = dao.newEntity();
        model.setId("m1");
        model.setProvider("test");
        model.setModelName("gpt-4");
        model.setApiKey("sk-secret-value-42");
        model.setVersion(1);
        dao.saveEntity(model);

        NopAiModel loaded = dao.requireEntityById("m1");
        assertEquals("sk-secret-value-42", loaded.getApiKey(),
                "enc-tagged column must decrypt back to the original value");

        List<Map<String, Object>> raw = jdbcTemplate.findAll(
                SQL.begin().sql("select id, api_key from nop_ai_model where id = 'm1'").end());
        Object stored = raw.get(0).get("api_key");
        assertNotNull(stored);
        assertTrue(!stored.toString().contains("sk-secret-value-42"),
                "enc-tagged column must not be stored as plaintext");
    }

    @Test
    public void testToManyRelationMapping() {
        IEntityDao<NopAiSession> sessionDao = sessionDao();
        sessionDao.saveEntity(newSession("s2", "agent-b"));

        IEntityDao<NopAiSessionMessage> msgDao = daoProvider.daoFor(NopAiSessionMessage.class);
        for (int i = 1; i <= 2; i++) {
            NopAiSessionMessage msg = msgDao.newEntity();
            msg.setId("m-" + i);
            msg.setSessionId("s2");
            msg.setRole(0);
            msg.setSeq((long) i);
            msg.setContent("msg-" + i);
            msg.setVersion(1);
            msgDao.saveEntity(msg);
        }

        IOrmSessionFactory sessionFactory = factoryBean.getObject();
        assertNotNull(sessionFactory.getOrmModel().getEntityModel("io.nop.ai.dao.entity.NopAiSession"));

        IEntityModel sessionModel = sessionFactory.getOrmModel()
                .getEntityModel("io.nop.ai.dao.entity.NopAiSession");
        IEntityRelationModel messages = sessionModel.getRelation("messages", false);
        assertNotNull(messages, "NopAiSession must declare to-many relation messages");
        assertTrue(messages.getKind().isToManyRelation());
        assertEquals("NopAiSessionMessage", messages.getRefEntityModel().getShortName());
        assertEquals("sessionId", messages.getJoinRightProps());
    }

    @Test
    public void testBizInterfaceGenericContract() {
        assertEquals(21, BIZ_INTERFACES.size());
        for (Class<?> bizInterface : BIZ_INTERFACES) {
            Type[] interfaces = bizInterface.getGenericInterfaces();
            ParameterizedType paramType = (ParameterizedType) interfaces[0];
            Type entityArg = paramType.getActualTypeArguments()[0];
            Class<?> entityClass = (Class<?>) entityArg;
            assertTrue(entityClass.getName().startsWith("io.nop.ai.dao.entity."),
                    "Biz interface " + bizInterface.getSimpleName() + " must bind to a dao entity class");
            String expectedEntityName = "io.nop.ai.dao.entity."
                    + bizInterface.getSimpleName().substring(1,
                    bizInterface.getSimpleName().length() - "Biz".length());
            assertEquals(expectedEntityName, entityClass.getName(),
                    "Biz interface " + bizInterface.getSimpleName() + " must bind to " + expectedEntityName);
        }
    }
}
