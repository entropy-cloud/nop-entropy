package io.nop.ai.gateway.channel;

import io.nop.ai.dao.entity.NopAiChannelSession;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
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
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.dao.OrmDaoProvider;
import io.nop.orm.factory.DefaultOrmColumnBinderEnhancer;
import io.nop.orm.factory.OrmSessionFactoryBean;
import io.nop.orm.impl.OrmTemplateImpl;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2 persistence test for {@link ChannelSessionStoreImpl}: verifies the
 * session-mapping lifecycle against an in-memory H2 database whose schema is
 * created from the live ORM model (so the {@code nop_ai_channel_session} table
 * and its {@code (channelType, channelId)} unique key are exercised for real,
 * not stubbed).
 *
 * <p>Covers the Anti-Hollow contract: miss → null, saveMapping → real
 * persisted row (re-readable), updateLastActive → real refresh that throws on
 * miss rather than silently no-op'ing.
 */
public class TestChannelSessionStore {

    private SimpleDataSource dataSource;
    private OrmSessionFactoryBean factoryBean;
    private IOrmTemplate orm;
    private IDaoProvider daoProvider;
    private ChannelSessionStoreImpl store;

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
        dataSource.setUrl("jdbc:h2:mem:channel-store-test-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new io.nop.api.core.ioc.IBeanProvider() {
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
        factoryBean.setGlobalCache(new LocalCacheProvider("channel-store-test", CacheConfig.newConfig(100)));
        factoryBean.setSequenceGenerator(new io.nop.dao.seq.UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
        factoryBean.init();

        IOrmSessionFactory sessionFactory = factoryBean.getObject();
        orm = new OrmTemplateImpl(sessionFactory);
        daoProvider = new OrmDaoProvider(orm);

        // create ALL tables (incl. nop_ai_channel_session) from the live ORM model
        Collection<? extends IEntityModel> tables = sessionFactory.getOrmModel().getEntityModelsInTopoOrder();
        String createSql = new io.nop.orm.ddl.DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null))
                .createTables(tables, false);
        jdbcTemplate.executeMultiSql(new SQL(createSql));

        store = new ChannelSessionStoreImpl();
        store.setDaoProvider(daoProvider);
        store.setOrmTemplate(orm);
    }

    @AfterEach
    void tearDown() {
        if (factoryBean != null) {
            factoryBean.destroy();
        }
    }

    @Test
    void findByChannelMissReturnsNull() {
        // explicit not-found state, not an exception nor a silent empty session
        ChannelSession found = store.findByChannel("feishu", "oc_missing");
        assertNull(found, "findByChannel must return null on miss");
    }

    @Test
    void saveMappingThenFindByChannelReusesSameSessionId() {
        // sessionId originates from the engine ack (passed in by the connector);
        // the store never generates it
        store.saveMapping("feishu", "oc_1", "sess-abc-123", "agent-1");

        ChannelSession found = store.findByChannel("feishu", "oc_1");
        assertNotNull(found, "findByChannel must find the saved mapping");
        assertEquals("sess-abc-123", found.getSessionId(),
                "hit must reuse the same sessionId that was saved");
        assertEquals("feishu", found.getChannelType());
        assertEquals("oc_1", found.getChannelId());
        assertEquals("agent-1", found.getAgentName());
        assertNotNull(found.getLastActiveAt(), "saveMapping must seed lastActiveAt");

        // persistence is real: the row is in the DB, not just in memory
        IEntityDao<NopAiChannelSession> dao = daoProvider.daoFor(NopAiChannelSession.class);
        QueryBean q = new QueryBean();
        q.setFilter(io.nop.api.core.beans.FilterBeans.eq("channelId", "oc_1"));
        assertEquals(1, dao.findAllByQuery(q).size(),
                "saveMapping must persist exactly one row for the channel");
    }

    @Test
    void updateLastActiveRefreshesTimestamp() throws Exception {
        store.saveMapping("feishu", "oc_2", "sess-def", "agent-1");

        ChannelSession before = store.findByChannel("feishu", "oc_2");
        assertNotNull(before.getLastActiveAt());

        // ensure clock advances so the refreshed timestamp is strictly newer
        Thread.sleep(15);

        store.updateLastActive("feishu", "oc_2");

        ChannelSession after = store.findByChannel("feishu", "oc_2");
        Timestamp refreshed = after.getLastActiveAt();
        assertNotNull(refreshed, "updateLastActive must persist a timestamp");
        assertTrue(refreshed.after(before.getLastActiveAt()),
                "updateLastActive must refresh lastActiveAt to a later value");
    }

    @Test
    void updateLastActiveOnMissThrowsRatherThanSilentNoop() {
        // a miss must surface, not silently succeed (Anti-Hollow)
        assertThrows(NopException.class,
                () -> store.updateLastActive("feishu", "oc_no_such"));
    }

    @Test
    void saveMappingRejectsEmptySessionId() {
        // the store must not invent a sessionId; an empty one is a contract violation
        assertThrows(NopException.class,
                () -> store.saveMapping("feishu", "oc_3", "", "agent-1"));
    }
}
