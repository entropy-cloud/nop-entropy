package io.nop.ai.service.credential;

import io.nop.ai.api.credential.IAiModelCredentialResolver;
import io.nop.ai.dao.entity.NopAiModel;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.ai.service.credential.AiModelCredentialResolverImpl.ERR_AI_CREDENTIAL_FIELD_EMPTY;
import static io.nop.ai.service.credential.AiModelCredentialResolverImpl.ERR_AI_CREDENTIAL_PROVIDER_NOT_AVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W7-successor（plan 2026-08-13-1118-3 Phase 2）：{@link AiModelCredentialResolverImpl} 焦点测试。
 *
 * <p>用真实 ORM 栈（OrmSessionFactoryBean + H2，DDL 从 nop-ai ORM 模型生成）seed NopAiModel 行，
 * 注入 recording fake {@link ICredentialProvider}，覆盖：
 * <ul>
 *   <li><b>resolution</b>：模型配 credentialId → resolver 返回凭证库解析的 apiKey（接线验证 Rule #23：
 *       ICredentialProvider.getCredentialData 在运行时确实被调用）</li>
 *   <li><b>fallback</b>：无 NopAiModel 行（.llm.xml↔NopAiModel 身份不对应，Phase 1 D4）→ null；
 *       credentialId 列为空 → null（正常兼容路径）</li>
 *   <li><b>fail-closed</b>（Phase 1 D5）：credentialId 非空但凭证缺失（provider 抛 NopException）→ 传播；
 *       凭证存在但 apiKey 字段空 → {@link #ERR_AI_CREDENTIAL_FIELD_EMPTY}；
 *       credentialId 非空但 provider 未部署 → {@link #ERR_AI_CREDENTIAL_PROVIDER_NOT_AVAILABLE}</li>
 * </ul>
 *
 * <p>查询侧「落行」验证：真实 NopAiModel 表查询（provider+modelName 精确匹配，Phase 1 D4）。
 */
public class TestAiModelCredentialResolver {

    /** fake-only error code simulating ICredentialProvider's fail-closed missing-credential behavior. */
    static final ErrorCode ERR_TEST_CREDENTIAL_NOT_FOUND =
            ErrorCode.define("ERR_TEST_CREDENTIAL_NOT_FOUND", "credential not found", "credentialId");

    private SimpleDataSource dataSource;
    private OrmSessionFactoryBean factoryBean;
    private IDaoProvider daoProvider;

    @BeforeAll
    static void init() {
        // 仅初始化到 VFS/component 级别（低于 IoC），避免 nop-sys 的 SysDictLoader 拉起
        // 需 datasource 配置的全局容器（本测试自建 ORM 栈，不依赖全局 IoC）。与同模块
        // TestNopAiBizModelEntityCrud 一致。
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
        dataSource.setUrl("jdbc:h2:mem:ai-cred-resolver-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new NoopBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("ai-cred-resolver-test", CacheConfig.newConfig(100)));
        factoryBean.setSequenceGenerator(new UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
        factoryBean.init();

        IOrmSessionFactory sessionFactory = factoryBean.getObject();
        IOrmTemplate orm = new OrmTemplateImpl(sessionFactory);
        daoProvider = new OrmDaoProvider(orm);

        Collection<? extends io.nop.orm.model.IEntityModel> tables =
                sessionFactory.getOrmModel().getEntityModelsInTopoOrder();
        String createSql = new DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null)).createTables(tables, false);
        jdbcTemplate.executeMultiSql(new io.nop.core.lang.sql.SQL(createSql));
    }

    @AfterEach
    void tearDown() {
        if (factoryBean != null) {
            factoryBean.destroy();
        }
    }

    private void seedModel(String id, String provider, String modelName, String credentialId) {
        IEntityDao<NopAiModel> dao = daoProvider.daoFor(NopAiModel.class);
        NopAiModel m = dao.newEntity();
        m.setId(id);
        m.setProvider(provider);
        m.setModelName(modelName);
        m.setCredentialId(credentialId);
        m.setVersion(1);
        dao.saveEntity(m);
    }

    private AiModelCredentialResolverImpl newResolver(RecordingCredentialProvider provider) {
        AiModelCredentialResolverImpl r = new AiModelCredentialResolverImpl();
        r.setDaoProvider(daoProvider);
        r.setCredentialProvider(provider);
        return r;
    }

    @Test
    void resolvesApiKeyFromCredentialWhenConfigured() {
        seedModel("m1", "test", "gpt-4", "cred-001");
        RecordingCredentialProvider provider = new RecordingCredentialProvider();
        provider.data.put("cred-001", credData("sk-from-credential-lib"));
        IAiModelCredentialResolver resolver = newResolver(provider);

        String apiKey = resolver.resolveApiKeyByCredential("test", "gpt-4");

        assertEquals("sk-from-credential-lib", apiKey,
                "configured credentialId must resolve to the credential library apiKey");
        assertEquals(1, provider.getCredentialDataCalls.size(),
                "ICredentialProvider.getCredentialData must be invoked at runtime (wiring Rule #23)");
        assertEquals("cred-001", provider.getCredentialDataCalls.get(0).credentialId);
        assertEquals("apiKey", provider.getCredentialDataCalls.get(0).field);
    }

    @Test
    void fallsBackWhenNoNopAiModelRow() {
        // identity mismatch: model exists only in .llm.xml, no NopAiModel row (Phase 1 D4)
        RecordingCredentialProvider provider = new RecordingCredentialProvider();
        IAiModelCredentialResolver resolver = newResolver(provider);

        String apiKey = resolver.resolveApiKeyByCredential("test", "gpt-4");

        assertNull(apiKey, "no NopAiModel row => explicit fallback (null) + audit, not an error");
        assertTrue(provider.getCredentialDataCalls.isEmpty(),
                "credential lib must NOT be consulted when no credentialId is configured");
    }

    @Test
    void fallsBackWhenCredentialIdEmpty() {
        seedModel("m2", "test", "gpt-4", null); // row exists but credentialId not set
        RecordingCredentialProvider provider = new RecordingCredentialProvider();
        IAiModelCredentialResolver resolver = newResolver(provider);

        String apiKey = resolver.resolveApiKeyByCredential("test", "gpt-4");

        assertNull(apiKey, "empty credentialId => normal compat path, fallback to resolveApiKey");
        assertTrue(provider.getCredentialDataCalls.isEmpty());
    }

    @Test
    void failClosedWhenCredentialMissing() {
        seedModel("m3", "test", "gpt-4", "cred-missing");
        RecordingCredentialProvider provider = new RecordingCredentialProvider();
        // cred-missing not in data map => provider throws NOT_FOUND (simulating soft-deleted/missing)
        provider.missingIds.add("cred-missing");
        IAiModelCredentialResolver resolver = newResolver(provider);

        NopException ex = assertThrows(NopException.class,
                () -> resolver.resolveApiKeyByCredential("test", "gpt-4"));
        assertTrue(ex.getMessage().contains("cred-missing") || ex.getParam("credentialId") != null,
                "fail-closed must propagate credential-not-found rather than silently using a wrong key");
    }

    @Test
    void failClosedWhenApiKeyFieldEmpty() {
        seedModel("m4", "test", "gpt-4", "cred-empty-field");
        RecordingCredentialProvider provider = new RecordingCredentialProvider();
        provider.data.put("cred-empty-field", credData(null)); // credential exists but apiKey field is null
        IAiModelCredentialResolver resolver = newResolver(provider);

        NopException ex = assertThrows(NopException.class,
                () -> resolver.resolveApiKeyByCredential("test", "gpt-4"));
        assertEquals(ERR_AI_CREDENTIAL_FIELD_EMPTY.getErrorCode(), ex.getErrorCode(),
                "credential configured but apiKey field empty => strong fail-closed (no silent fallback)");
    }

    /**
     * D6-04（A1-audit successor，2026-08-17）：apiKey 字段值为纯空白 → 同空值处理，
     * fail-closed（isEmpty → isBlank 收紧：空白值不得当有效 key 使用）。
     */
    @Test
    void failClosedWhenApiKeyFieldBlank() {
        seedModel("m4b", "test", "gpt-4", "cred-blank-field");
        RecordingCredentialProvider provider = new RecordingCredentialProvider();
        provider.data.put("cred-blank-field", credData("   ")); // 纯空白 apiKey
        IAiModelCredentialResolver resolver = newResolver(provider);

        NopException ex = assertThrows(NopException.class,
                () -> resolver.resolveApiKeyByCredential("test", "gpt-4"));
        assertEquals(ERR_AI_CREDENTIAL_FIELD_EMPTY.getErrorCode(), ex.getErrorCode(),
                "blank (whitespace-only) apiKey field must fail closed like an empty one (D6-04 isBlank)");
    }

    @Test
    void failClosedWhenProviderNotDeployed() {
        seedModel("m5", "test", "gpt-4", "cred-005");
        AiModelCredentialResolverImpl resolver = new AiModelCredentialResolverImpl();
        resolver.setDaoProvider(daoProvider);
        resolver.setCredentialProvider(null); // nop-credential-service not deployed

        NopException ex = assertThrows(NopException.class,
                () -> resolver.resolveApiKeyByCredential("test", "gpt-4"));
        assertEquals(ERR_AI_CREDENTIAL_PROVIDER_NOT_AVAILABLE.getErrorCode(), ex.getErrorCode(),
                "credentialId set but credential lib missing => strong fail-closed (deployment inconsistency)");
    }

    @Test
    void nullProviderWithEmptyCredentialIdFallsBack() {
        // no credentialId configured AND provider not deployed => normal fallback (not fail-closed)
        seedModel("m6", "test", "gpt-4", null);
        AiModelCredentialResolverImpl resolver = new AiModelCredentialResolverImpl();
        resolver.setDaoProvider(daoProvider);
        resolver.setCredentialProvider(null);

        assertNull(resolver.resolveApiKeyByCredential("test", "gpt-4"),
                "empty credentialId with no provider => normal fallback, not an error");
    }

    private static CredentialData credData(String apiKey) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (apiKey != null) {
            fields.put("apiKey", apiKey);
        }
        return new CredentialData(fields);
    }

    private static final class CredentialDataCall {
        final String credentialId;
        final String field;

        CredentialDataCall(String credentialId, String field) {
            this.credentialId = credentialId;
            this.field = field;
        }
    }

    /**
     * Recording fake for {@link ICredentialProvider}. Tracks getCredentialData invocations (wiring Rule #23)
     * and simulates missing-credential fail-closed via {@link ICredentialProvider} semantics.
     */
    private static final class RecordingCredentialProvider implements ICredentialProvider {
        final Map<String, CredentialData> data = new LinkedHashMap<>();
        final List<String> missingIds = new ArrayList<>();
        final List<CredentialDataCall> getCredentialDataCalls = new ArrayList<>();

        @Override
        public CredentialData getCredential(String credentialId) {
            if (missingIds.contains(credentialId) || !data.containsKey(credentialId)) {
                // simulate ICredentialProvider fail-closed semantics (missing/soft-deleted credential)
                throw new NopException(ERR_TEST_CREDENTIAL_NOT_FOUND)
                        .param("credentialId", credentialId);
            }
            return data.get(credentialId);
        }

        @Override
        public Object getCredentialData(String credentialId, String field) {
            getCredentialDataCalls.add(new CredentialDataCall(credentialId, field));
            return getCredential(credentialId).getField(field);
        }

        @Override
        public TestResult testCredential(String credentialId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MaskedCredential mask(String credentialId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerUsage(String credentialId, String consumerRef) {
        }

        @Override
        public void unregisterUsage(String credentialId, String consumerRef) {
        }
    }

    private static final class NoopBeanProvider implements io.nop.api.core.ioc.IBeanProvider {
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
    }
}
