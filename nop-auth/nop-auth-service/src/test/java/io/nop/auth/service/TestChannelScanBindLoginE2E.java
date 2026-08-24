package io.nop.auth.service;

import io.nop.ai.gateway.login.ChannelLoginApiBizModel;
import io.nop.ai.gateway.login.ScanLoginResult;
import io.nop.api.core.audit.AuditRequest;
import io.nop.api.core.audit.IAuditService;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.bind.BindStartResult;
import io.nop.auth.api.bind.ChannelBindingInfo;
import io.nop.auth.api.messages.AccessCodeRequest;
import io.nop.auth.api.messages.LoginResult;
import io.nop.auth.api.messages.LoginUserInfo;
import io.nop.auth.core.jwt.JwtAuthTokenProvider;
import io.nop.auth.core.login.AuthToken;
import io.nop.auth.core.login.IAuthTokenProvider;
import io.nop.auth.core.login.ILoginSessionStore;
import io.nop.auth.core.login.IUserContextCache;
import io.nop.auth.core.login.LocalUserContextCache;
import io.nop.auth.core.login.SessionInfo;
import io.nop.auth.core.login.UserContextConfig;
import io.nop.auth.core.password.IPasswordEncoder;
import io.nop.auth.core.password.SHA256PasswordEncoder;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.service.biz.LoginApiBizModel;
import io.nop.auth.service.channel.ChannelBindServiceImpl;
import io.nop.auth.service.login.LoginServiceImpl;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.core.unittest.VarCollector;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.unittest.VarCollector;
import io.nop.integration.api.bind.BindTicket;
import io.nop.integration.api.bind.ChannelScanCallback;
import io.nop.integration.api.channel.ChannelTypeCodes;
import io.nop.integration.feishu.bind.FeishuBindProvider;
import io.nop.integration.feishu.client.FeishuCredentials;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.factory.DefaultOrmColumnBinderEnhancer;
import io.nop.orm.factory.OrmSessionFactoryBean;
import io.nop.orm.impl.OrmTemplateImpl;
import io.nop.orm.dao.OrmDaoProvider;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W6-2 end-to-end test for the QR-scan channel binding + scan-login full chain.
 * Assembles the <b>real</b> production beans in a single JVM over a real in-memory
 * H2 database (the monolithic-deployment topology the roadmap assumes), with the
 * network boundary (Feishu OAuth) simulated by documented callback payloads:
 *
 * <ul>
 *   <li><b>W6-2a binding E2E</b> — real {@link ChannelBindServiceImpl} + real
 *       {@link FeishuBindProvider}: {@code startBinding} &rarr; simulated Feishu scan
 *       callback &rarr; {@code completeBinding} writes a {@code NopAuthExtLogin} row;
 *       read-back ORM verification; {@code findBinding} reverse lookup; rebind
 *       adjudication (idempotent same-user / explicit cross-user failure).</li>
 *   <li><b>W6-2b login full-chain E2E</b> — real {@link ChannelLoginApiBizModel#loginByScanAsync}
 *       (W4 endpoint) &rarr; {@code findBinding} hit &rarr; real
 *       {@link LoginServiceImpl#createSessionForUserAsync} (real DB user/roles load)
 *       &rarr; real {@link IAuthTokenProvider#generateAccessCode} &rarr; the deferred
 *       {@code ILoginSpi.getLoginResultAsync(AccessCodeRequest)} chain (real
 *       {@link LoginApiBizModel}) that decodes the code and reads the session back
 *       from the shared {@link IUserContextCache} &rarr; {@link LoginResult}. Repays
 *       the two items Plan 5 deferred to W6-2.</li>
 * </ul>
 *
 * <p><b>Single-JVM cache topology</b>: the bootstrap write side
 * ({@code LoginServiceImpl.saveUserContextAsync}) and the consumption read side
 * ({@code LoginApiBizModel.getLoginResultAsync} &rarr; {@code getUserContextAsync})
 * share one in-memory {@link LocalUserContextCache} instance, so a session saved by
 * the bootstrap is immediately visible to the consumer (cache hit is observable).
 *
 * <p><b>No silent skip</b>: a missing binding for the scanned extId makes
 * {@code loginByScan} throw {@link NopException} rather than returning an empty code.
 *
 * <p><b>Test assembly</b>: follows the established W6-1/W6-3 E2E pattern
 * ({@code io.nop.ai.gateway.channel.feishu.TestFeishuConversationE2E$E2EH2SessionStore}):
 * a self-contained H2 + {@link OrmSessionFactoryBean} stack that creates <b>all</b>
 * tables from the live ORM model via {@code DdlSqlCreator}, with the real auth/gateway
 * beans wired around it. This bypasses the full application container (whose
 * eager beans query {@code NopAuthSite} before schema creation in the current
 * ALL_LAZY test mode — a pre-existing infra issue affecting the whole auth-service
 * module) while still exercising the real production classes end to end. The only
 * non-real collaborators are off the critical chain: a no-op {@link IAuditService}
 * (never invoked by {@code createSessionForUserAsync}) and a UUID-minting
 * {@link ILoginSessionStore} (the session id it returns is what the accessCode is
 * signed over; the session body lives in the real {@link IUserContextCache}).
 *
 * <p>The four-file login-main-flow contract ({@code ILoginService} /
 * {@code ILoginSpi} / {@code IAuthTokenProvider} / {@code LoginApiBizModel}) is
 * unchanged — the Phase&nbsp;0 hash proof reconfirms the baseline.
 */
class TestChannelScanBindLoginE2E {

    private static final String CHANNEL = FeishuBindProvider.CHANNEL_TYPE;
    private static final int LOGIN_TYPE_FEISHU = ChannelTypeCodes.LOGIN_TYPE_FEISHU;

    // ---- H2 + ORM stack ----
    private SimpleDataSource dataSource;
    private OrmSessionFactoryBean factoryBean;
    private OrmTemplateImpl ormTemplate;
    private IDaoProvider daoProvider;

    // ---- real beans under test ----
    private FeishuBindProvider feishuBindProvider;
    private ChannelBindServiceImpl channelBindService;
    private JwtAuthTokenProvider authTokenProvider;
    private LocalUserContextCache userContextCache;
    private LoginServiceImpl loginService;
    private LoginApiBizModel loginApiBizModel;
    private ChannelLoginApiBizModel channelLoginApi;

    @BeforeAll
    static void initCore() {
        // initialise VFS + config + model registry, but stop BEFORE the IoC container
        // (so the full app container — and its eager NopAuthSite query — is never built)
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroyCore() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        // 模拟 AutoTestCase 类先于本类运行后的 JVM 状态：VarCollector 被 complete() 置空。
        // LoginApiBizModel.buildLoginResult 必须容忍 VarCollector.instance() 为 null，
        // 否则全量套件中本 E2E 在 AutoTestCase 之后执行时会 NPE（回归防护）。
        VarCollector.registerInstance(null);
        buildH2Stack();
        wireRealBeans();
    }

    @AfterEach
    void tearDown() {
        if (factoryBean != null) {
            try {
                factoryBean.destroy();
            } catch (Exception ignored) {
                // best-effort teardown
            }
        }
    }

    // ===================== Phase 1: W6-2a binding E2E =====================

    @Test
    void testScanBindingE2EWritesExtLoginAndFindsBinding() {
        String userId = "e2e-bind-user";
        String openId = "ou_e2e_bind_primary";
        saveUser(userId);

        BindStartResult start = channelBindService.startBinding(CHANNEL, userId);
        assertNotNull(start.getTicketId(), "startBinding must return a ticketId");
        assertNotNull(start.getQrPayload(), "startBinding must return a qrPayload");
        assertTrue(start.getQrPayload().contains("state=" + start.getTicketId()),
                "qrPayload must carry state=ticketId for callback correlation");

        // simulate the documented Feishu scan callback (open_id + ticketId)
        ChannelScanCallback callback = feishuCallback(start.getTicketId(), openId);

        ChannelBindingInfo binding = channelBindService.completeBinding(CHANNEL, userId, openId);
        assertNotNull(binding, "completeBinding must return non-null binding info");
        assertEquals(userId, binding.getPlatformUserId());
        assertEquals(openId, binding.getExtId());
        assertEquals(CHANNEL, binding.getChannelType());

        // read-back via real ORM
        io.nop.auth.dao.entity.NopAuthExtLogin row =
                daoProvider.daoFor(io.nop.auth.dao.entity.NopAuthExtLogin.class).getEntityById(binding.getBindingId());
        assertNotNull(row, "NopAuthExtLogin row must exist after completeBinding");
        assertEquals(userId, row.getUserId());
        assertEquals(LOGIN_TYPE_FEISHU, row.getLoginType().intValue());
        assertEquals(openId, row.getExtId());
        assertEquals(Boolean.TRUE, row.getVerified());
        assertEquals(Byte.valueOf((byte) 0), row.getDelFlag());

        // findBinding reverse-lookup hits the just-written binding
        ChannelBindingInfo found = channelBindService.findBinding(CHANNEL, openId);
        assertNotNull(found, "findBinding must hit the binding written by completeBinding");
        assertEquals(userId, found.getPlatformUserId());
        assertEquals(binding.getBindingId(), found.getBindingId());
    }

    @Test
    void testRebindAdjudicationIdempotentAndCrossUserFails() {
        String userA = "e2e-rebind-a";
        String userB = "e2e-rebind-b";
        String openId = "ou_e2e_rebind";
        saveUser(userA);
        saveUser(userB);

        channelBindService.startBinding(CHANNEL, userA);
        ChannelBindingInfo first = channelBindService.completeBinding(CHANNEL, userA, openId);
        assertNotNull(first);
        long rowsAfterFirst = countExtLoginRows(openId);

        // same user + same extId again -> idempotent, returns existing, no new row
        channelBindService.startBinding(CHANNEL, userA);
        ChannelBindingInfo again = channelBindService.completeBinding(CHANNEL, userA, openId);
        assertEquals(first.getBindingId(), again.getBindingId(),
                "idempotent rescan must return the same bindingId");
        assertEquals(rowsAfterFirst, countExtLoginRows(openId),
                "idempotent rescan must not insert a second row");

        // cross-user attempt for the same extId -> explicit failure, not a silent no-op
        NopException ex = assertThrows(NopException.class,
                () -> channelBindService.completeBinding(CHANNEL, userB, openId));
        assertTrue(ex.getMessage().contains("already bound to another user"),
                "cross-user rebind must fail explicitly; got: " + ex.getMessage());
    }

    // ===================== Phase 2: W6-2b login full-chain E2E =====================

    @Test
    void testScanLoginFullChainE2E() {
        String userId = "e2e-login-user";
        String openId = "ou_e2e_login";
        saveUser(userId);

        // precondition: establish the binding (the scan-login flow looks it up)
        BindStartResult bindStart = channelBindService.startBinding(CHANNEL, userId);
        feishuBindProvider.onChannelScanCallback(feishuCallback(bindStart.getTicketId(), openId));
        channelBindService.completeBinding(CHANNEL, userId, openId);

        // --- first scan-login: full chain (wrapped in an ORM session so the real
        //     LoginServiceImpl can lazy-load the user's roles) ---
        String accessCode = ormTemplate.runInSession(session -> scanLoginForAccessCode(userId, openId));
        assertNotNull(accessCode, "loginByScan must return a non-null accessCode");
        assertTrue(!accessCode.isEmpty(), "accessCode must be non-empty");

        // wiring: generateAccessCode really ran -> the code decodes back to a session id
        AuthToken parsed = authTokenProvider.parseAccessCode(accessCode);
        assertNotNull(parsed.getSessionId(), "parseAccessCode must yield the session id");

        // cache hit: the session written by createSessionForUserAsync is visible to
        // the consumption side in this single JVM
        assertNotNull(FutureHelper.syncGet(userContextCache.getUserContextAsync(parsed.getSessionId())),
                "IUserContextCache must contain the bootstrapped session (single-JVM cache hit)");

        // the deferred getLoginResultAsync chain -> LoginResult (Plan 5 deferred repaid)
        LoginResult loginResult = getLoginResult(accessCode);
        assertNotNull(loginResult, "LoginResult must be non-null");
        assertNotNull(loginResult.getAccessToken(), "LoginResult.accessToken must be non-empty");
        assertTrue(!loginResult.getAccessToken().isEmpty(), "accessToken must be non-empty");
        LoginUserInfo info = loginResult.getUserInfo();
        assertNotNull(info, "LoginResult.userInfo must be non-null");
        assertEquals(userId, info.getUserId(), "LoginResult must carry the bound platform user");

        // --- second scan over the SAME binding: direct login, no rebind ---
        long rowsBefore = countExtLoginRows(openId);
        String accessCode2 = ormTemplate.runInSession(session -> scanLoginForAccessCode(userId, openId));
        assertNotNull(accessCode2);
        assertNotEquals(accessCode, accessCode2, "second scan must mint a fresh accessCode");
        LoginResult loginResult2 = getLoginResult(accessCode2);
        assertNotNull(loginResult2.getAccessToken(), "secondary scan must also yield a LoginResult");
        assertEquals(rowsBefore, countExtLoginRows(openId),
                "secondary scan-login must not insert another binding row");
    }

    @Test
    void testScanLoginWithoutBindingFailsExplicitly() {
        String userId = "e2e-unbound-user";
        String unboundOpenId = "ou_e2e_never_bound";
        saveUser(userId);

        // mint a ticket so the provider accepts the callback, but the open_id is NOT bound
        BindTicket ticket = feishuBindProvider.createBindTicket(CHANNEL, userId);
        ChannelScanCallback callback = feishuCallback(ticket.getTicketId(), unboundOpenId);

        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(session ->
                        FutureHelper.syncGet(channelLoginApi.loginByScanAsync(callback, null))));
        assertTrue(ex.getMessage().contains("no effective channel binding"),
                "unbound scan-login must fail explicitly; got: " + ex.getMessage());
    }

    // ===================== H2 + ORM stack =====================

    /**
     * Build a self-contained in-memory H2 + ORM session factory, creating ALL tables
     * (incl. nop_auth_ext_login, nop_auth_user, nop_auth_session, ...) from the live
     * ORM model. Mirrors the E2EH2SessionStore pattern from the gateway W6-1/W6-3 E2E.
     */
    private void buildH2Stack() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:e2e-bind-login-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MinimalBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("e2e-bind-login", CacheConfig.newConfig(100)));
        factoryBean.setSequenceGenerator(new io.nop.dao.seq.UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
        factoryBean.init();

        IOrmSessionFactory sessionFactory = factoryBean.getObject();
        ormTemplate = new OrmTemplateImpl(sessionFactory);

        // create ALL tables from the live ORM model (nop_auth_* and friends)
        Collection<? extends IEntityModel> tables = sessionFactory.getOrmModel().getEntityModelsInTopoOrder();
        String createSql = new io.nop.orm.ddl.DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null))
                .createTables(tables, false);
        jdbcTemplate.executeMultiSql(new SQL(createSql));

        OrmDaoProvider __daoProvider = new OrmDaoProvider(); __daoProvider.setOrmTemplate(ormTemplate); daoProvider = __daoProvider;
    }

    /** Wire the REAL production beans around the H2 stack. */
    private void wireRealBeans() {
        // real FeishuBindProvider
        feishuBindProvider = new FeishuBindProvider();
        FeishuCredentials creds = new FeishuCredentials();
        creds.setAppId("cli_test_e2e");
        creds.setAppSecret("test-secret");
        feishuBindProvider.setCredentials(creds);
        feishuBindProvider.setTicketTtlMs(60_000L);

        // real ChannelBindServiceImpl + real FeishuBindProvider
        channelBindService = new ChannelBindServiceImpl();
        channelBindService.setDaoProvider(daoProvider);
        channelBindService.setChannelBindProviders(Collections.singletonList(feishuBindProvider));

        // real JwtAuthTokenProvider
        authTokenProvider = new JwtAuthTokenProvider();
        authTokenProvider.setEncKey("test-enc-key-for-scan-login-e2e");

        // real LocalUserContextCache (single-JVM shared cache)
        userContextCache = new LocalUserContextCache();
        userContextCache.setUserContextConfig(new UserContextConfig());
        userContextCache.init();

        // real LoginServiceImpl (ISessionBootstrap) — only off-chain collaborators
        // are stubbed (audit + sessionStore); everything on the scan-login chain is real
        loginService = new LoginServiceImpl();
        setField(loginService, "daoProvider", daoProvider);
        setField(loginService, "passwordEncoder", new SHA256PasswordEncoder());
        setField(loginService, "auditService", new NoopAuditService());
        setField(loginService, "authTokenProvider", authTokenProvider);
        setField(loginService, "userContextCache", userContextCache);
        setField(loginService, "loginSessionStore", new UuidSessionStore());
        // return-user-id defaults to true via @InjectValue in production; replicate
        // here so getUserInfo populates userId on the returned LoginResult
        loginService.setReturnUserId(true);
        loginService.setReturnDeptName(false);

        // real LoginApiBizModel (ILoginSpi) — consumes via getLoginResultAsync
        loginApiBizModel = new LoginApiBizModel();
        setField(loginApiBizModel, "loginService", loginService);

        // real ChannelLoginApiBizModel (W4 endpoint)
        channelLoginApi = new ChannelLoginApiBizModel();
        setField(channelLoginApi, "authTokenProvider", authTokenProvider);
        setField(channelLoginApi, "sessionBootstrap", loginService);
        setField(channelLoginApi, "channelBindService", channelBindService);
        channelLoginApi.setAccessCodeExpireSeconds(300);
        channelLoginApi.registerProvider(feishuBindProvider);
    }

    // ===================== helpers =====================

    private void saveUser(String userId) {
        IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
        NopAuthUser user = dao.newEntity();
        user.setUserId(userId);
        user.setUserName("user_" + userId);
        user.setNickName("user_" + userId);
        user.setPassword("123");
        user.setOpenId(userId);
        user.setUserType(1);
        user.setStatus(1);
        user.setGender(1);
        user.setTenantId("0");
        dao.saveEntity(user);
    }

    /** Mint a fresh scan ticket (login-page QR), build the Feishu callback, drive loginByScan. */
    private String scanLoginForAccessCode(String userId, String openId) {
        BindTicket ticket = feishuBindProvider.createBindTicket(CHANNEL, userId);
        ChannelScanCallback callback = feishuCallback(ticket.getTicketId(), openId);
        ScanLoginResult result = FutureHelper.syncGet(channelLoginApi.loginByScanAsync(callback, null));
        return result.getAccessCode();
    }

    private LoginResult getLoginResult(String accessCode) {
        AccessCodeRequest req = new AccessCodeRequest();
        req.setAccessCode(accessCode);
        // getLoginResultAsync does no ORM (JWT parse + cache read), so a direct call
        // on the real LoginApiBizModel (ILoginSpi) is safe and exercises the same
        // code path the production consumer reaches. IServiceContext.getRequestHeaders
        // is passed through but not used by the accessCode consumption path.
        IServiceContext context = new ServiceContextImpl();
        return FutureHelper.syncGet(loginApiBizModel.getLoginResultAsync(req, context));
    }

    private static ChannelScanCallback feishuCallback(String ticketId, String openId) {
        ChannelScanCallback cb = new ChannelScanCallback();
        cb.setChannelType(CHANNEL);
        cb.setTicketId(ticketId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("open_id", openId);
        cb.setRawPayload(payload);
        return cb;
    }

    private long countExtLoginRows(String openId) {
        io.nop.auth.dao.entity.NopAuthExtLogin example = new io.nop.auth.dao.entity.NopAuthExtLogin();
        example.setExtId(openId);
        example.orm_disableLogicalDelete(true);
        List<io.nop.auth.dao.entity.NopAuthExtLogin> rows =
                daoProvider.daoFor(io.nop.auth.dao.entity.NopAuthExtLogin.class).findAllByExample(example);
        return rows.size();
    }

    private static void setField(Object target, String name, Object value) {
        Class<?> cls = target.getClass();
        while (cls != null) {
            try {
                java.lang.reflect.Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            } catch (IllegalAccessException e) {
                throw NopException.adapt(e);
            }
        }
        throw new IllegalArgumentException("no field " + name + " on " + target.getClass());
    }

    // ===================== off-chain stubs =====================

    /**
     * Minimal {@link IAuditService}: the scan-login bootstrap path
     * ({@code createSessionForUserAsync}) never audits, so a no-op keeps the wiring
     * simple without touching the critical chain.
     */
    static class NoopAuditService implements IAuditService {
        @Override
        public boolean isAllProcessed() {
            return true;
        }

        @Override
        public void saveAudit(AuditRequest request) {
            // no-op: not on the scan-login chain
        }
    }

    /**
     * Minimal {@link ILoginSessionStore}: {@code saveSession} mints the session id
     * that the accessCode is signed over and that the {@link IUserContextCache} is
     * keyed by. {@code getActionSessions} returns empty so {@code autoLogout} is a
     * no-op. The session body (roles/tokens) lives in the real cache, not here.
     */
    static class UuidSessionStore implements ILoginSessionStore {
        @Override
        public SessionInfo getSessionInfoForUser(String userName) {
            return null;
        }

        @Override
        public String saveSession(io.nop.api.core.auth.IUserContext userContext,
                                  io.nop.auth.api.messages.LoginRequest request,
                                  Map<String, Object> headers) {
            return StringHelper.generateUUID();
        }

        @Override
        public void logoutSession(String sessionId, int logoutType, String logoutUser) {
            // no-op
        }

        @Override
        public List<String> getActionSessions(String userName) {
            return Collections.emptyList();
        }
    }

    /**
     * Minimal {@link io.nop.api.core.ioc.IBeanProvider} for the
     * {@link OrmSessionFactoryBean}: instantiates any requested bean type via its
     * no-arg constructor. The session factory only asks for optional collaborators
     * (column binder enhancer etc.) that are passed explicitly via setters, so this
     * provider is never exercised on the critical path. Mirrors the E2EH2SessionStore
     * pattern.
     */
    static class MinimalBeanProvider implements io.nop.api.core.ioc.IBeanProvider {
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
