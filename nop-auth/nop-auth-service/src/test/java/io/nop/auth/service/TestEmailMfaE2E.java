package io.nop.auth.service;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.LoginResult;
import io.nop.auth.api.messages.MfaVerifyRequest;
import io.nop.auth.core.jwt.JwtAuthTokenProvider;
import io.nop.auth.core.login.ILoginSessionStore;
import io.nop.auth.core.login.LocalUserContextCache;
import io.nop.auth.core.login.SessionInfo;
import io.nop.auth.core.login.UserContextConfig;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.core.mfa.store.LocalEmailCodeStore;
import io.nop.auth.core.mfa.store.LocalMfaChallengeStore;
import io.nop.auth.core.mfa.store.LocalSmsCodeStore;
import io.nop.auth.core.mfa.store.MfaChallenge;
import io.nop.auth.core.password.SHA256PasswordEncoder;
import io.nop.auth.core.totp.TOTPAuthenticator;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthUser;
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
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.integration.api.email.EmailMessage;
import io.nop.integration.api.email.IEmailSender;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W15-impl Phase 1 E2E：邮件验证码因子全链（设计 §5.3.3）——bindMfa(email) 发码（收件目标
 * 服务端解析 + 文案模板替换断言）→ confirmMfa → 登录拦截（mfaType=email）→ sendMfaCode
 * email 分派 → mfaVerify(email code) → accessToken；三层限流（interval/daily/IP 维度按入口
 * 生效）；IEmailSender 未装配 fail-closed；email-code.enabled=false 显式拒绝；一期零回归
 * （未绑 email 用户无感知）。
 * <p>
 * Anti-Hollow：所有断言经真实容器组件（注入的 CapturingEmailSender 测试实现断言收件目标
 * 与 {code} 占位替换；EmailCodeStore 发码/消费口径与 MfaFactorVerifier 一致）。
 */
class TestEmailMfaE2E {

    private static final String TENANT_ID = "0";
    private static final String USER_EMAIL = "mfa.user@example.com";

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
    private CapturingEmailSender emailSender;
    private NopAuthUserBizModel userBizModel;
    private LoginServiceImpl loginService;
    private LoginApiBizModel loginApiBizModel;

    @BeforeAll
    static void initCore() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        IConfigProvider provider = AppConfig.getConfigProvider();
        originalMfaEnabled = provider.getConfigValue("nop.auth.mfa.enabled", Boolean.FALSE);
        originalEmailEnabled = provider.getConfigValue("nop.auth.email-code.enabled", Boolean.FALSE);
        originalEmailInterval = provider.getConfigValue("nop.auth.email-code.send-interval-seconds", 60);
        provider.assignConfigValue("nop.auth.mfa.enabled", true);
        provider.assignConfigValue("nop.auth.email-code.enabled", true);
        // 缺省 0：限流专项用例内临时调整，避免拖慢其余链路
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

    // ===================== 全链：bind(email) → confirm → login → sendMfaCode → mfaVerify =====================

    @Test
    void testEmailFactorFullChain() {
        String userId = "email-mfa-user";
        String userName = "email_mfa_user";
        saveUser(userId, userName);

        // 1. bindMfa(email) → 发码到服务端解析的登记 email（模板 {code} 替换断言）
        MfaBindResult bind = ormTemplate.runInSession(s ->
                userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_EMAIL, ctx(userId, userName)));
        assertEquals(NopAuthConstants.MFA_TYPE_EMAIL, bind.getMfaType());
        assertTrue(bind.isEmailSent(), "bindMfa(email) must report emailSent=true");
        assertFalse(bind.isSmsSent());
        EmailMessage sent = emailSender.lastMessage.get();
        assertNotNull(sent, "bindMfa(email) must call IEmailSender");
        assertEquals(Collections.singletonList(USER_EMAIL), sent.getTo(),
                "recipient must be the server-resolved registered email");
        String code = extractCode(sent.getText());
        assertFalse(sent.getText().contains("{code}"), "text template {code} placeholder must be replaced");
        assertFalse(sent.getSubject().contains("{code}"), "subject template must not retain placeholder");

        // pending setting（mfaType=email，phone=null）
        NopAuthMfaSetting pending = daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        assertEquals(NopAuthConstants.MFA_STATUS_PENDING, pending.getStatus());
        assertEquals(NopAuthConstants.MFA_TYPE_EMAIL, pending.getMfaType());
        assertNull(pending.getPhone(), "email factor must not populate the sms phone column");

        // 2. confirmMfa(email code) → enabled + 恢复码
        List<String> recoveryCodes = ormTemplate.runInSession(s ->
                userBizModel.confirmMfa(bind.getBindToken(), code, ctx(userId, userName)));
        assertEquals(10, recoveryCodes.size());
        assertEquals(NopAuthConstants.MFA_STATUS_ENABLED,
                daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId).getStatus());

        // 3. 密码登录 → MFA_REQUIRED（mfaType=email）
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doPasswordLogin(userName)));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertEquals(NopAuthConstants.MFA_TYPE_EMAIL, ex.getParam(NopAuthErrors.ARG_MFA_TYPE));
        String challengeToken = (String) ex.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN);
        assertNotNull(mfaChallengeStore.peek(challengeToken));

        // 4. sendMfaCode → email 分派（key=mfa-email:userId 与 MfaFactorVerifier 消费口径一致）
        emailSender.lastMessage.set(null);
        loginService.sendMfaCode(challengeToken, "127.0.0.1");
        EmailMessage resent = emailSender.lastMessage.get();
        assertNotNull(resent, "sendMfaCode(email challenge) must send via IEmailSender");
        assertEquals(Collections.singletonList(USER_EMAIL), resent.getTo());
        String verifyCode = extractCode(resent.getText());

        // 5. mfaVerify(email code) → accessToken（challenge 消费一次性）
        LoginResult result = ormTemplate.runInSession(s -> doMfaVerify(challengeToken, verifyCode));
        assertNotNull(result.getAccessToken(), "email-factor mfaVerify must yield accessToken");
        assertNull(mfaChallengeStore.peek(challengeToken), "challenge consumed after successful mfaVerify");
    }

    // ===================== fail-closed：IEmailSender 未装配 =====================

    @Test
    void testEmailSenderAbsentFailsClosed() {
        String userId = "email-fc-user";
        saveUser(userId, "email_fc_user");
        setField(userBizModel, "emailSender", null);
        setField(loginService, "emailSender", null);
        try {
            NopException ex = assertThrows(NopException.class, () ->
                    ormTemplate.runInSession(s -> userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_EMAIL, ctx(userId, "email_fc_user"))));
            assertEquals(NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST.getErrorCode(), ex.getErrorCode());
            assertTrue(String.valueOf(ex.getParam("msg")).contains("IEmailSender"),
                    "fail-closed error must be explicit: " + ex.getParam("msg"));
        } finally {
            setField(userBizModel, "emailSender", emailSender);
            setField(loginService, "emailSender", emailSender);
        }
    }

    // ===================== 门控：email-code.enabled=false 显式拒绝 =====================

    @Test
    void testEmailCodeDisabledRejectsExplicitly() {
        String userId = "email-gate-user";
        saveUser(userId, "email_gate_user");
        IConfigProvider provider = AppConfig.getConfigProvider();
        provider.assignConfigValue("nop.auth.email-code.enabled", false);
        try {
            // bindMfa(email) 入口
            NopException bind = assertThrows(NopException.class, () ->
                    userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_EMAIL, ctx(userId, "email_gate_user")));
            assertEquals(NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST.getErrorCode(), bind.getErrorCode());
            assertTrue(String.valueOf(bind.getParam("msg")).contains("email-code.enabled"));

            // sendMfaCode email 分支入口（直接构造 email challenge）
            setupEnabledEmailUser("email-gate-login", "email_gate_login");
            NopException login = assertThrows(NopException.class, () ->
                    ormTemplate.runInSession(s -> doPasswordLogin("email_gate_login")));
            String challengeToken = (String) login.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN);
            NopException send = assertThrows(NopException.class, () ->
                    loginService.sendMfaCode(challengeToken, "127.0.0.1"));
            assertEquals(NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST.getErrorCode(), send.getErrorCode());
            assertTrue(String.valueOf(send.getParam("msg")).contains("email-code.enabled"),
                    "gate error must be explicit, not silent: " + send.getParam("msg"));
        } finally {
            provider.assignConfigValue("nop.auth.email-code.enabled", true);
        }
    }

    // ===================== 限流三层 =====================

    @Test
    void testEmailRateLimitInterval() {
        String userId = "email-rate-user";
        saveUser(userId, "email_rate_user");
        IConfigProvider provider = AppConfig.getConfigProvider();
        provider.assignConfigValue("nop.auth.email-code.send-interval-seconds", 60);
        try {
            // 第一次 bindMfa(email) 发码成功
            assertDoesNotThrow(() -> ormTemplate.runInSession(s -> userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_EMAIL, ctx(userId, "email_rate_user"))));
            // 60s 内第二次 → RATE_LIMITED（bindMfa 入口限流）
            NopException limited = assertThrows(NopException.class, () ->
                    ormTemplate.runInSession(s -> userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_EMAIL, ctx(userId, "email_rate_user"))));
            assertEquals(NopAuthErrors.ERR_AUTH_EMAIL_RATE_LIMITED.getErrorCode(), limited.getErrorCode());
        } finally {
            provider.assignConfigValue("nop.auth.email-code.send-interval-seconds", 0);
        }
    }

    @Test
    void testEmailDailyLimit() {
        String userId = "email-daily-user";
        saveUser(userId, "email_daily_user");
        IConfigProvider provider = AppConfig.getConfigProvider();
        provider.assignConfigValue("nop.auth.email-code.daily-limit", 1);
        try {
            assertDoesNotThrow(() -> ormTemplate.runInSession(s -> userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_EMAIL, ctx(userId, "email_daily_user"))));
            // interval 已设 0；第二次超过 daily-limit=1 → DAILY_LIMIT
            NopException limited = assertThrows(NopException.class, () ->
                    ormTemplate.runInSession(s -> userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_EMAIL, ctx(userId, "email_daily_user"))));
            assertEquals(NopAuthErrors.ERR_AUTH_EMAIL_DAILY_LIMIT.getErrorCode(), limited.getErrorCode());
        } finally {
            provider.assignConfigValue("nop.auth.email-code.daily-limit", 20);
        }
    }

    @Test
    void testSendMfaCodeEmailRateLimit() {
        String userId = "email-sendrl-user";
        setupEnabledEmailUser(userId, "email_sendrl_user");
        NopException login = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doPasswordLogin("email_sendrl_user")));
        String challengeToken = (String) login.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN);

        IConfigProvider provider = AppConfig.getConfigProvider();
        provider.assignConfigValue("nop.auth.email-code.send-interval-seconds", 60);
        try {
            loginService.sendMfaCode(challengeToken, "127.0.0.1");
            NopException limited = assertThrows(NopException.class, () ->
                    loginService.sendMfaCode(challengeToken, "127.0.0.1"));
            assertEquals(NopAuthErrors.ERR_AUTH_EMAIL_RATE_LIMITED.getErrorCode(), limited.getErrorCode(),
                    "sendMfaCode email branch must respect the send interval");
        } finally {
            provider.assignConfigValue("nop.auth.email-code.send-interval-seconds", 0);
        }
    }

    // ===================== 错误码与边界 =====================

    @Test
    void testMfaVerifyWrongEmailCodeFails() {
        String userId = "email-wrong-user";
        setupEnabledEmailUser(userId, "email_wrong_user");
        NopException login = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doPasswordLogin("email_wrong_user")));
        String challengeToken = (String) login.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN);
        loginService.sendMfaCode(challengeToken, "127.0.0.1");

        // 错码 → MFA_FAIL（不消费 challenge）
        NopException fail = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doMfaVerify(challengeToken, "000000")));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), fail.getErrorCode());
        assertNotNull(mfaChallengeStore.peek(challengeToken), "challenge survives a wrong-code attempt");
    }

    @Test
    void testExpiredEmailCodeThrowsEmailExpiredError() {
        String userId = "email-exp-user";
        setupEnabledEmailUser(userId, "email_exp_user");
        NopException login = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doPasswordLogin("email_exp_user")));
        String challengeToken = (String) login.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN);

        // 不发码直接 verify → EmailCodeStore EXPIRED → ERR_AUTH_EMAIL_CODE_EXPIRED（email 专属码）
        NopException expired = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doMfaVerify(challengeToken, "123456")));
        assertEquals(NopAuthErrors.ERR_AUTH_EMAIL_CODE_EXPIRED.getErrorCode(), expired.getErrorCode());
    }

    @Test
    void testBindEmailWithoutRegisteredEmailRejected() {
        String userId = "email-noaddr-user";
        saveUserWithoutEmail(userId, "email_noaddr_user");
        NopException ex = assertThrows(NopException.class, () ->
                userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_EMAIL, ctx(userId, "email_noaddr_user")));
        assertEquals(NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST.getErrorCode(), ex.getErrorCode());
        assertTrue(String.valueOf(ex.getParam("msg")).contains("no email address"));
    }

    // ===================== 一期零回归 =====================

    @Test
    void testZeroRegressionUserWithoutEmailFactor() {
        // 未绑 email 因子用户：登录路径无感知（无 challenge、无 email 发送）
        String userId = "plain-email-regression";
        saveUserWithoutEmail(userId, "plain_email_user");
        LoginResult result = assertDoesNotThrow(() ->
                ormTemplate.runInSession(s -> doPasswordLogin("plain_email_user")));
        assertNotNull(result.getAccessToken(), "user without email factor must login directly");
        assertNull(emailSender.lastMessage.get(), "no email must be sent for non-email users");
        assertNull(daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId));
    }

    @Test
    void testBindMfaWhitelistStillRejectsUnknownType() {
        String userId = "email-wl-user";
        saveUser(userId, "email_wl_user");
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> userBizModel.bindMfa("bogus-type", ctx(userId, "email_wl_user"))));
        assertEquals(NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST.getErrorCode(), ex.getErrorCode());
        assertTrue(String.valueOf(ex.getParam("msg")).contains("totp/sms/webauthn/email"),
                "error message must enumerate the extended whitelist: " + ex.getParam("msg"));
    }

    // ===================== Helpers =====================

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

    private void saveUserWithoutEmail(String userId, String userName) {
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

    private LoginResult doMfaVerify(String challengeToken, String code) {
        MfaVerifyRequest req = new MfaVerifyRequest();
        req.setChallengeToken(challengeToken);
        req.setCode(code);
        IServiceContext ctx = new ServiceContextImpl();
        return FutureHelper.syncGet(loginApiBizModel.mfaVerifyAsync(req, ctx));
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

    /** 从正文模板提取 6 位验证码（默认模板 "Your verification code is {code}. ..."）。 */
    private static String extractCode(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d{6})\\b").matcher(text);
        assertTrue(m.find(), "email text must embed the 6-digit code: " + text);
        return m.group(1);
    }

    // ===================== H2 + wiring（TestMfaUserSelfService 同型） =====================

    private void buildH2Stack() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:mfa-email-e2e-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MinimalBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("mfa-email", CacheConfig.newConfig(100)));
        factoryBean.setSequenceGenerator(new io.nop.dao.seq.UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
        factoryBean.init();

        IOrmSessionFactory sessionFactory = factoryBean.getObject();
        ormTemplate = new OrmTemplateImpl(sessionFactory);

        Collection<? extends IEntityModel> tables = sessionFactory.getOrmModel().getEntityModelsInTopoOrder();
        String createSql = new io.nop.orm.ddl.DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null))
                .createTables(tables, false);
        jdbcTemplate.executeMultiSql(new io.nop.core.lang.sql.SQL(createSql));

        daoProvider = new OrmDaoProvider(ormTemplate);
    }

    private void wireRealBeans() {
        JwtAuthTokenProvider authTokenProvider = new JwtAuthTokenProvider();
        authTokenProvider.setEncKey("test-enc-key-mfa-email");

        LocalUserContextCache userContextCache = new LocalUserContextCache();
        userContextCache.setUserContextConfig(new UserContextConfig());
        userContextCache.init();

        mfaChallengeStore = new LocalMfaChallengeStore();
        emailCodeStore = new LocalEmailCodeStore();
        emailSender = new CapturingEmailSender();
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

    // ===================== Stubs =====================

    static class CapturingEmailSender implements IEmailSender {
        final AtomicReference<EmailMessage> lastMessage = new AtomicReference<>();

        @Override
        public void sendEmail(EmailMessage mail) {
            lastMessage.set(mail);
        }
    }

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
