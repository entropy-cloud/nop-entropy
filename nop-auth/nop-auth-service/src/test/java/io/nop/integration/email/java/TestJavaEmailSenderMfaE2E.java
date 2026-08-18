package io.nop.integration.email.java;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.LoginResult;
import io.nop.auth.core.jwt.JwtAuthTokenProvider;
import io.nop.auth.core.login.ILoginSessionStore;
import io.nop.auth.core.login.LocalUserContextCache;
import io.nop.auth.core.login.SessionInfo;
import io.nop.auth.core.login.UserContextConfig;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.core.mfa.store.LocalEmailCodeStore;
import io.nop.auth.core.mfa.store.LocalMfaChallengeStore;
import io.nop.auth.core.mfa.store.LocalSmsCodeStore;
import io.nop.auth.core.password.SHA256PasswordEncoder;
import io.nop.auth.core.totp.TOTPAuthenticator;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.service.NopAuthConstants;
import io.nop.auth.service.NopAuthErrors;
import io.nop.auth.service.biz.LoginApiBizModel;
import io.nop.auth.service.biz.dto.MfaBindResult;
import io.nop.auth.service.entity.NopAuthUserBizModel;
import io.nop.auth.service.login.LoginServiceImpl;
import io.nop.auth.service.mfa.MfaFactorVerifier;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.VarCollector;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.txn.ITransactionTemplate;
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

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_RESOLVE_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W16-impl-ext Phase 4：Email 家族 MFA 发码链 E2E（Minimum Rules #22 端到端 + #23 接线验证）——
 * 从凭证库"行"（fake provider 预设 smtp-email 凭证）经 credentialId 解析（JavaEmailSender
 * withTransport 期）到真实消费方（{@code NopAuthUserBizModel.bindMfa} 与
 * {@code LoginServiceImpl.sendMfaCode} 发码路径）的完整链路。
 *
 * <p>落 nop-auth-service 测试树（真实消费方所在模块），包位置 {@code io.nop.integration.email.java}
 * （访问 JavaEmailSender 包可见 seam {@code connectTransport(ResolvedCredential)}——同
 * TestTencentSmsSenderCredential 的同包先例）。{@code RecordingTransport} 捕获解析后的凭证组
 * 与实际发出的 MimeMessage（收件人 + 6 位验证码断言）。
 */
class TestJavaEmailSenderMfaE2E {

    private static final String TENANT_ID = "0";
    private static final String USER_EMAIL = "cred.mfa.user@example.com";
    private static final String CONSUMER_REF = "integration:smtp-email";

    private static Boolean originalMfaEnabled;
    private static Boolean originalEmailEnabled;
    private static Integer originalEmailInterval;

    // ---- H2 + ORM stack ----
    private SimpleDataSource dataSource;
    private OrmSessionFactoryBean factoryBean;
    private OrmTemplateImpl ormTemplate;
    private IDaoProvider daoProvider;

    // ---- real beans ----
    private LocalMfaChallengeStore mfaChallengeStore;
    private LocalEmailCodeStore emailCodeStore;
    private CredentialWiredEmailSender emailSender;
    private NopAuthUserBizModel userBizModel;
    private LoginServiceImpl loginService;
    private LoginApiBizModel loginApiBizModel;

    /** 手写 fake provider：smtp-email 凭证"行"。 */
    static class FakeProvider implements ICredentialProvider {
        CredentialData data = new CredentialData("smtp-email",
                new LinkedHashMap<>(Map.of("username", "cred-smtp-user", "password", "cred-smtp-pass")));
        NopException error;
        int getCredentialCalls;
        int registerUsageCalls;
        String lastRegisterCredentialId;
        String lastRegisterConsumerRef;

        @Override
        public CredentialData getCredential(String credentialId) {
            getCredentialCalls++;
            if (error != null) {
                throw error;
            }
            return data;
        }

        @Override
        public Object getCredentialData(String credentialId, String field) {
            return getCredential(credentialId).getField(field);
        }

        @Override
        public TestResult testCredential(String credentialId) {
            throw new UnsupportedOperationException("not used in test");
        }

        @Override
        public MaskedCredential mask(String credentialId) {
            throw new UnsupportedOperationException("not used in test");
        }

        @Override
        public void registerUsage(String credentialId, String consumerRef) {
            registerUsageCalls++;
            lastRegisterCredentialId = credentialId;
            lastRegisterConsumerRef = consumerRef;
        }

        @Override
        public void unregisterUsage(String credentialId, String consumerRef) {
        }
    }

    /** 记录型 Transport：捕获 sendMessage 的真实 MimeMessage（不触网络）。 */
    static class RecordingTransport extends Transport {
        final List<Message> sentMessages = new java.util.ArrayList<>();

        RecordingTransport() {
            super(Session.getInstance(new Properties()), null);
        }

        @Override
        public void sendMessage(Message msg, javax.mail.Address[] addresses) {
            sentMessages.add(msg);
        }
    }

    /**
     * credentialId 接线的 JavaEmailSender（E2E 的邮件出口）：MailConfig.credentialId 指向
     * fake provider 的 smtp-email 凭证；connectTransport seam 捕获解析后的凭证组并返回记录型
     * Transport——证明解析值真实到达 Transport 构造点、真实 MimeMessage 经此发出。
     */
    static class CredentialWiredEmailSender extends JavaEmailSender {
        final FakeProvider provider = new FakeProvider();
        final RecordingTransport transport = new RecordingTransport();
        volatile ResolvedCredential lastCredential;

        CredentialWiredEmailSender() {
            MailConfig config = new MailConfig();
            config.setHost("smtp.example.com");
            config.setPort(587);
            config.setUsername("static-user");
            config.setPassword("static-pass");
            config.setCredentialId("cred-smtp-1");
            setConfig(config);
            setCredentialProvider(provider);
            init(); // @PostConstruct 登记（容器外手动触发）
        }

        @Override
        protected Transport connectTransport(ResolvedCredential credential) {
            this.lastCredential = credential;
            return transport;
        }
    }

    @BeforeAll
    static void initCore() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        IConfigProvider provider = AppConfig.getConfigProvider();
        originalMfaEnabled = provider.getConfigValue("nop.auth.mfa.enabled", Boolean.FALSE);
        originalEmailEnabled = provider.getConfigValue("nop.auth.email-code.enabled", Boolean.FALSE);
        originalEmailInterval = provider.getConfigValue("nop.auth.email-code.send-interval-seconds", 60);
        provider.assignConfigValue("nop.auth.mfa.enabled", true);
        provider.assignConfigValue("nop.auth.email-code.enabled", true);
        provider.assignConfigValue("nop.auth.email-code.send-interval-seconds", 0);
    }

    @AfterAll
    static void destroyCore() {
        IConfigProvider provider = AppConfig.getConfigProvider();
        provider.assignConfigValue("nop.auth.mfa.enabled",
                originalMfaEnabled != null ? originalMfaEnabled : Boolean.FALSE);
        provider.assignConfigValue("nop.auth.email-code.enabled",
                originalEmailEnabled != null ? originalEmailEnabled : Boolean.FALSE);
        provider.assignConfigValue("nop.auth.email-code.send-interval-seconds",
                originalEmailInterval != null ? originalEmailInterval : 60);
    }

    @BeforeEach
    void setUp() {
        if (VarCollector.instance() == null) {
            VarCollector.registerInstance(new VarCollector());
        }
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

    // ===================== E2E：bindMfa(email) 发码消费 credentialId 凭证 =====================

    @Test
    void testBindMfaEmailConsumesCredentialResolvedGroup() throws Exception {
        String userId = "cred-mfa-user";
        saveUser(userId, "cred_mfa_user");

        // 1. bindMfa(email) → 真实发码路径 → IEmailSender(credentialId 接线) 发送
        MfaBindResult bind = ormTemplate.runInSession(s ->
                userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_EMAIL, ctx(userId, "cred_mfa_user")));
        assertTrue(bind.isEmailSent(), "bindMfa(email) must report emailSent=true");

        // 2. 接线验证：解析后的凭证组到达 Transport 构造点（非静态值）
        assertNotNull(emailSender.lastCredential, "send chain must drive the credential resolution");
        assertEquals("cred-smtp-user", emailSender.lastCredential.username,
                "resolved username (from the credential row) must reach the Transport construction point");
        assertEquals("cred-smtp-pass", emailSender.lastCredential.password,
                "resolved password must reach the Transport construction point");

        // 3. 端到端：真实 MimeMessage 经解析凭证驱动的 Transport 发出（收件人 + 6 位验证码）
        assertEquals(1, emailSender.transport.sentMessages.size());
        Message sent = emailSender.transport.sentMessages.get(0);
        assertEquals(USER_EMAIL, sent.getRecipients(Message.RecipientType.TO)[0].toString(),
                "recipient must be the registered email");
        String content = String.valueOf(sent.getContent());
        assertTrue(java.util.regex.Pattern.compile("\\b(\\d{6})\\b").matcher(content).find(),
                "email content must embed the 6-digit code: " + content);

        // 4. 登记语义：bean 初始化登记 usage（integration:smtp-email）
        assertEquals(1, emailSender.provider.registerUsageCalls);
        assertEquals("cred-smtp-1", emailSender.provider.lastRegisterCredentialId);
        assertEquals(CONSUMER_REF, emailSender.provider.lastRegisterConsumerRef);
    }

    // ===================== E2E：登录挑战 sendMfaCode(email) 消费 credentialId 凭证 =====================

    @Test
    void testLoginChallengeSendMfaCodeConsumesCredentialResolvedGroup() throws Exception {
        String userId = "cred-mfa-login-user";
        setupEnabledEmailUser(userId, "cred_mfa_login");

        // 密码登录 → MFA_REQUIRED（email 因子）
        NopException login = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doPasswordLogin("cred_mfa_login")));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), login.getErrorCode());
        String challengeToken = (String) login.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN);

        // LoginServiceImpl.sendMfaCode email 分派 → credentialId 凭证 → Transport。
        // 惰性证明：bean 初始化（init 仅登记）后零解析调用——解析发生在发送期
        emailSender.transport.sentMessages.clear();
        assertEquals(0, emailSender.provider.getCredentialCalls,
                "no resolution at bean init (lazy per-send resolution family)");
        loginService.sendMfaCode(challengeToken, "127.0.0.1");
        assertEquals(1, emailSender.transport.sentMessages.size(),
                "sendMfaCode(email challenge) must send through the credential-wired sender");
        assertEquals("cred-smtp-user", emailSender.lastCredential.username);
        assertEquals("cred-smtp-pass", emailSender.lastCredential.password);
        assertEquals(1, emailSender.provider.getCredentialCalls,
                "one send = one resolution (lazy, next-send rotation visibility)");
    }

    // ===================== E2E 负例：解析失败 fail-closed 贯穿发码链（不误信已发码） =====================

    @Test
    void testResolutionFailureFailsClosedThroughSendChain() {
        String userId = "cred-mfa-broken-user";
        saveUser(userId, "cred_mfa_broken");
        emailSender.provider.error = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED,
                new RuntimeException("credential deleted"));
        try {
            NopException ex = assertThrows(NopException.class, () ->
                    ormTemplate.runInSession(s ->
                            userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_EMAIL, ctx(userId, "cred_mfa_broken"))));
            // fail-closed 经真实消费方链路显式抛出（NopException 穿透 withTransport，未被吞成日志）
            assertEquals(ERR_CREDENTIAL_RESOLVE_FAILED.getErrorCode(), ex.getErrorCode());
            assertEquals(0, emailSender.transport.sentMessages.size(),
                    "no email must be sent when resolution fails (fail-closed, no silent fallback)");
        } finally {
            emailSender.provider.error = null;
        }
    }

    // ===================== Helpers（TestEmailMfaE2E 同型） =====================

    private void setupEnabledEmailUser(String userId, String userName) {
        saveUser(userId, userName);
        ormTemplate.runInSession(session -> {
            ContextProvider.runWithTenant(TENANT_ID, () -> {
                IEntityDao<NopAuthMfaSetting> dao = daoProvider.daoFor(NopAuthMfaSetting.class);
                NopAuthMfaSetting setting = dao.newEntity();
                setting.setUserId(userId);
                setting.setMfaType(NopAuthConstants.MFA_TYPE_EMAIL);
                setting.setStatus(NopAuthConstants.MFA_STATUS_ENABLED);
                setting.setTenantId(TENANT_ID);
                dao.saveEntity(setting);
                return null;
            });
            return null;
        });
    }

    private void saveUser(String userId, String userName) {
        SHA256PasswordEncoder encoder = new SHA256PasswordEncoder();
        String salt = encoder.generateSalt();
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
            NopAuthUser user = dao.newEntity();
            user.setUserId(userId);
            user.setUserName(userName);
            user.setNickName(userName);
            user.setPassword(encoder.encodePassword(salt, "123"));
            user.setSalt(salt);
            user.setOpenId(userId);
            user.setUserType(1);
            user.setStatus(1);
            user.setGender(1);
            user.setTenantId(TENANT_ID);
            user.setEmail(USER_EMAIL);
            dao.saveEntity(user);
            return null;
        });
    }

    private LoginResult doPasswordLogin(String userName) {
        LoginRequest req = new LoginRequest();
        req.setLoginType(AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD);
        req.setPrincipalId(userName);
        req.setPrincipalSecret("123");
        IServiceContext ctx = new ServiceContextImpl();
        return FutureHelper.syncGet(loginApiBizModel.loginAsync(req, ctx));
    }

    private IServiceContext ctx(String userId, String userName) {
        ServiceContextImpl c = new ServiceContextImpl();
        UserContextImpl uc = new UserContextImpl();
        uc.setUserId(userId);
        uc.setUserName(userName);
        uc.setTenantId(TENANT_ID);
        Set<String> roles = new HashSet<>();
        roles.add(NopAuthConstants.ROLE_ADMIN);
        uc.setRoles(roles);
        c.setUserContext(uc);
        return c;
    }

    private void buildH2Stack() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:mfa-email-cred-e2e-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MinimalBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("mfa-email-cred", CacheConfig.newConfig(100)));
        factoryBean.setSequenceGenerator(new io.nop.dao.seq.UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
        factoryBean.init();

        IOrmSessionFactory sessionFactory = factoryBean.getObject();
        ormTemplate = new OrmTemplateImpl(sessionFactory);

        java.util.Collection<? extends IEntityModel> tables = sessionFactory.getOrmModel().getEntityModelsInTopoOrder();
        String createSql = new io.nop.orm.ddl.DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null))
                .createTables(tables, false);
        jdbcTemplate.executeMultiSql(new io.nop.core.lang.sql.SQL(createSql));

        daoProvider = new OrmDaoProvider(ormTemplate);
    }

    private void wireRealBeans() {
        JwtAuthTokenProvider authTokenProvider = new JwtAuthTokenProvider();
        authTokenProvider.setEncKey("test-enc-key-mfa-email-cred");

        LocalUserContextCache userContextCache = new LocalUserContextCache();
        userContextCache.setUserContextConfig(new UserContextConfig());
        userContextCache.init();

        mfaChallengeStore = new LocalMfaChallengeStore();
        emailCodeStore = new LocalEmailCodeStore();
        emailSender = new CredentialWiredEmailSender();
        SHA256PasswordEncoder passwordEncoder = new SHA256PasswordEncoder();

        MfaFactorVerifier mfaFactorVerifier = new MfaFactorVerifier();
        setField(mfaFactorVerifier, "totpAuthenticator", new TOTPAuthenticator());
        setField(mfaFactorVerifier, "smsCodeStore", new LocalSmsCodeStore());
        setField(mfaFactorVerifier, "emailCodeStore", emailCodeStore);
        setField(mfaFactorVerifier, "daoProvider", daoProvider);

        userBizModel = new NopAuthUserBizModel();
        setField(userBizModel, "daoProvider", daoProvider);
        setField(userBizModel, "passwordEncoder", passwordEncoder);
        setField(userBizModel, "totpAuthenticator", new TOTPAuthenticator());
        setField(userBizModel, "smsCodeStore", new LocalSmsCodeStore());
        setField(userBizModel, "emailCodeStore", emailCodeStore);
        setField(userBizModel, "emailSender", emailSender);
        setField(userBizModel, "mfaFactorVerifier", mfaFactorVerifier);

        loginService = new LoginServiceImpl();
        setField(loginService, "daoProvider", daoProvider);
        setField(loginService, "passwordEncoder", passwordEncoder);
        setField(loginService, "auditService", new NoopAuditService());
        setField(loginService, "authTokenProvider", authTokenProvider);
        setField(loginService, "userContextCache", userContextCache);
        setField(loginService, "loginSessionStore", new UuidSessionStore());
        setField(loginService, "mfaChallengeStore", mfaChallengeStore);
        setField(loginService, "smsCodeStore", new LocalSmsCodeStore());
        setField(loginService, "emailCodeStore", emailCodeStore);
        setField(loginService, "emailSender", emailSender);
        setField(loginService, "totpAuthenticator", new TOTPAuthenticator());
        setField(loginService, "mfaFactorVerifier", mfaFactorVerifier);
        loginService.setReturnDeptName(false);

        loginApiBizModel = new LoginApiBizModel();
        setField(loginApiBizModel, "loginService", loginService);
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

    // ===================== Stubs（TestEmailMfaE2E 同型） =====================

    static class NoopAuditService implements io.nop.api.core.audit.IAuditService {
        @Override
        public boolean isAllProcessed() {
            return true;
        }

        @Override
        public void saveAudit(io.nop.api.core.audit.AuditRequest request) {
        }
    }

    static class UuidSessionStore implements ILoginSessionStore {
        @Override
        public SessionInfo getSessionInfoForUser(String userName) {
            return null;
        }

        @Override
        public String saveSession(IUserContext userContext, LoginRequest request, Map<String, Object> headers) {
            return StringHelper.generateUUID();
        }

        @Override
        public void logoutSession(String sessionId, int logoutType, String logoutUser) {
        }

        @Override
        public List<String> getActionSessions(String userName) {
            return Collections.emptyList();
        }
    }

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
