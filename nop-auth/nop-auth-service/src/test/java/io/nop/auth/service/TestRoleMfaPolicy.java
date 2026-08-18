package io.nop.auth.service;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.auth.dao.entity.NopAuthRoleMfaPolicy;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.dao.entity.NopAuthUserRole;
import io.nop.auth.service.entity.NopAuthRoleBizModel;
import io.nop.auth.service.mfa.RoleMfaPolicy;
import io.nop.auth.service.mfa.RoleMfaPolicyEvaluator;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.core.CoreConstants;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W13-impl Phase 1 tests：角色级 MFA 策略（设计 §4.3）——
 * <ul>
 *   <li>{@link RoleMfaPolicyEvaluator#factorLevel} 强度表全量映射 + 未知值 fail-closed 0。</li>
 *   <li>evaluator 合并语义：单角色/多角色 max / allowTrustedDevice AND / 无策略零行 = NONE /
 *       角色快照口径（直接角色 + childRoleIds 继承 + 隐式 user 角色挂策略 = 全员强制）。</li>
 *   <li>{@link NopAuthRoleBizModel#saveMfaPolicy}/{@link NopAuthRoleBizModel#removeMfaPolicy}
 *       CRUD 幂等 + requireAdmin 权限拒绝（非 admin）/ 非法 minMfaLevel 拒绝。</li>
 *   <li>ORM round-trip（H2 真实建表）。</li>
 * </ul>
 */
class TestRoleMfaPolicy {

    private static final String TENANT_ID = "0";

    private SimpleDataSource dataSource;
    private OrmSessionFactoryBean factoryBean;
    private OrmTemplateImpl ormTemplate;
    private IDaoProvider daoProvider;
    private RoleMfaPolicyEvaluator evaluator;
    private NopAuthRoleBizModel roleBizModel;

    @BeforeAll
    static void initCore() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @BeforeEach
    void setUp() {
        buildH2Stack();

        evaluator = new RoleMfaPolicyEvaluator();
        setField(evaluator, "daoProvider", daoProvider);

        roleBizModel = new NopAuthRoleBizModel();
        setField(roleBizModel, "daoProvider", daoProvider);
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

    // ===================== factorLevel 强度表 =====================

    @Test
    void testFactorLevelTable() {
        assertEquals(1, RoleMfaPolicyEvaluator.factorLevel("sms"), "sms = channel-otp level 1");
        assertEquals(1, RoleMfaPolicyEvaluator.factorLevel("email"), "email = channel-otp level 1 (W15 factor, table lands now)");
        assertEquals(2, RoleMfaPolicyEvaluator.factorLevel("totp"), "totp = level 2");
        assertEquals(3, RoleMfaPolicyEvaluator.factorLevel("webauthn"), "webauthn = level 3 (W14 factor, table lands now)");
        assertEquals(0, RoleMfaPolicyEvaluator.factorLevel("unknown-type"), "unknown mfaType fail-closed = 0");
        assertEquals(0, RoleMfaPolicyEvaluator.factorLevel(""), "empty mfaType = 0");
        assertEquals(0, RoleMfaPolicyEvaluator.factorLevel(null), "null mfaType = 0");
    }

    // ===================== evaluator 合并语义 =====================

    @Test
    void testNoPolicyRowsYieldsNone() {
        String userId = "no-policy-user";
        saveUserWithRoles(userId, "no-policy-role");

        RoleMfaPolicy policy = ormTemplate.runInSession(s -> evaluator.evaluateForUser(userId));
        assertEquals(0, policy.getMaxLevel(), "no policy rows => maxLevel 0 (一期行为)");
        assertTrue(policy.isAllowTrustedDevice(), "no policy rows => allowTrustedDevice true");
        assertSame(RoleMfaPolicy.NONE, policy);
    }

    @Test
    void testSingleRolePolicy() {
        savePolicyRow("role-a", 2, true);
        String userId = "single-role-user";
        saveUserWithRoles(userId, "role-a");

        RoleMfaPolicy policy = ormTemplate.runInSession(s -> evaluator.evaluateForUser(userId));
        assertEquals(2, policy.getMaxLevel());
        assertTrue(policy.isAllowTrustedDevice());
    }

    @Test
    void testMultiRoleMaxAndAndMerge() {
        savePolicyRow("role-l1", 1, true);
        savePolicyRow("role-l3", 3, false);
        String userId = "multi-role-user";
        saveUserWithRoles(userId, "role-l1", "role-l3");

        RoleMfaPolicy policy = ormTemplate.runInSession(s -> evaluator.evaluateForUser(userId));
        assertEquals(3, policy.getMaxLevel(), "max(minMfaLevel) 合并——最严格角色胜");
        assertFalse(policy.isAllowTrustedDevice(), "allowTrustedDevice AND 合并——任一 false 即禁");
    }

    @Test
    void testChildRoleIdsInheritanceExpansion() {
        // 角色快照口径：直接角色 parent → childRoleIds 展开 child（策略挂 child 即命中）
        saveRole("parent-role", "child-role");
        savePolicyRow("child-role", 3, true);
        String userId = "inherit-user";
        saveUserWithRoles(userId, "parent-role");

        RoleMfaPolicy policy = ormTemplate.runInSession(s -> evaluator.evaluateForUser(userId));
        assertEquals(3, policy.getMaxLevel(), "childRoleIds 继承展开后的策略行参与合并（对齐 buildUserContext 口径）");
    }

    @Test
    void testImplicitUserRolePolicyAppliesToAll() {
        // 隐式 user 角色挂策略 = 全员强制（设计 §4.3——显式用法而非漏洞）
        savePolicyRow(NopAuthConstants.ROLE_USER, 2, true);
        String userId = "plain-user";
        saveUserWithRoles(userId); // 无任何直接角色

        RoleMfaPolicy policy = ormTemplate.runInSession(s -> evaluator.evaluateForUser(userId));
        assertEquals(2, policy.getMaxLevel(), "policy on implicit 'user' role applies to every user");
    }

    @Test
    void testUnknownUserYieldsNone() {
        RoleMfaPolicy policy = ormTemplate.runInSession(s -> evaluator.evaluateForUser("no-such-user"));
        assertSame(RoleMfaPolicy.NONE, policy, "unknown user => no policy to evaluate => NONE");
    }

    @Test
    void testOrmRoundTrip() {
        savePolicyRow("rt-role", 3, false);
        NopAuthRoleMfaPolicy row = ormTemplate.runInSession(s ->
                daoProvider.daoFor(NopAuthRoleMfaPolicy.class).getEntityById("rt-role"));
        assertNotNull(row, "policy row must round-trip through H2");
        assertEquals(3, row.getMinMfaLevel());
        assertEquals(0, row.getAllowTrustedDevice().byteValue(), "allowTrustedDevice=false persisted as 0");
        assertNotNull(row.getCreateTime(), "audit field createTime auto-filled by ORM");
    }

    // ===================== 策略管理面 CRUD + 权限 =====================

    @Test
    void testSaveMfaPolicyAdminCreatesAndUpdatesIdempotently() {
        IServiceContext admin = adminCtx("admin-id", "admin");

        ormTemplate.runInSession(s -> {
            roleBizModel.saveMfaPolicy("crud-role", 2, null, admin);
            return null;
        });
        NopAuthRoleMfaPolicy row = ormTemplate.runInSession(s ->
                daoProvider.daoFor(NopAuthRoleMfaPolicy.class).getEntityById("crud-role"));
        assertEquals(2, row.getMinMfaLevel());
        assertEquals(1, row.getAllowTrustedDevice().byteValue(), "null allowTrustedDevice defaults to true (1)");

        // update (idempotent upsert by roleId)
        ormTemplate.runInSession(s -> {
            roleBizModel.saveMfaPolicy("crud-role", 3, false, admin);
            return null;
        });
        NopAuthRoleMfaPolicy updated = ormTemplate.runInSession(s ->
                daoProvider.daoFor(NopAuthRoleMfaPolicy.class).getEntityById("crud-role"));
        assertEquals(3, updated.getMinMfaLevel());
        assertEquals(0, updated.getAllowTrustedDevice().byteValue());
    }

    @Test
    void testRemoveMfaPolicyDeletesRowAndIsIdempotent() {
        IServiceContext admin = adminCtx("admin-id", "admin");
        saveRole("del-role", null);
        savePolicyRow("del-role", 1, true);
        String userId = "del-policy-user";
        saveUserWithRoles(userId, "del-role");
        assertEquals(1, ormTemplate.runInSession(s -> evaluator.evaluateForUser(userId)).getMaxLevel(),
                "policy effective before remove");

        ormTemplate.runInSession(s -> {
            roleBizModel.removeMfaPolicy("del-role", admin);
            return null;
        });
        // 删行即撤策略：评估口径不再命中（逻辑删除行视同无策略）
        assertEquals(0, ormTemplate.runInSession(s -> evaluator.evaluateForUser(userId)).getMaxLevel(),
                "删行即撤策略——evaluator 不再命中该角色策略");
        NopAuthRoleMfaPolicy row = ormTemplate.runInSession(s ->
                daoProvider.daoFor(NopAuthRoleMfaPolicy.class).getEntityById("del-role"));
        if (row != null) {
            assertTrue(row.getDelFlag() != null && row.getDelFlag() != 0,
                    "remove must at least logically delete the row");
        }

        // idempotent second remove: explicit no-op, no throw
        assertDoesNotThrow(() -> ormTemplate.runInSession(s -> {
            roleBizModel.removeMfaPolicy("del-role", admin);
            return null;
        }));
    }

    @Test
    void testNonAdminSaveAndRemoveRejected() {
        savePolicyRow("guard-role", 1, true);
        IServiceContext plain = ctx("plain-id", "plain_user");

        NopException save = assertThrows(NopException.class, () -> ormTemplate.runInSession(s -> {
            roleBizModel.saveMfaPolicy("guard-role", 2, null, plain);
            return null;
        }));
        assertTrue(save.getMessage().contains("admin"), "non-admin save must be rejected: " + save.getMessage());

        NopException remove = assertThrows(NopException.class, () -> ormTemplate.runInSession(s -> {
            roleBizModel.removeMfaPolicy("guard-role", plain);
            return null;
        }));
        assertTrue(remove.getMessage().contains("admin"), "non-admin remove must be rejected");

        // row untouched
        assertEquals(1, ormTemplate.runInSession(s ->
                daoProvider.daoFor(NopAuthRoleMfaPolicy.class).getEntityById("guard-role")).getMinMfaLevel());
    }

    @Test
    void testInvalidMinMfaLevelRejected() {
        IServiceContext admin = adminCtx("admin-id", "admin");
        assertThrows(NopException.class, () -> ormTemplate.runInSession(s -> {
            roleBizModel.saveMfaPolicy("lvl-role", 0, null, admin);
            return null;
        }));
        assertThrows(NopException.class, () -> ormTemplate.runInSession(s -> {
            roleBizModel.saveMfaPolicy("lvl-role", 4, null, admin);
            return null;
        }));
        assertNull(ormTemplate.runInSession(s ->
                daoProvider.daoFor(NopAuthRoleMfaPolicy.class).getEntityById("lvl-role")), "invalid level must not persist");
    }

    // ===================== Helpers =====================

    private void savePolicyRow(String roleId, int minMfaLevel, boolean allowTrustedDevice) {
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthRoleMfaPolicy> dao = daoProvider.daoFor(NopAuthRoleMfaPolicy.class);
            NopAuthRoleMfaPolicy p = dao.newEntity();
            p.setRoleId(roleId);
            p.setMinMfaLevel(minMfaLevel);
            p.setAllowTrustedDevice(allowTrustedDevice ? (byte) 1 : (byte) 0);
            dao.saveEntity(p);
            return null;
        });
    }

    private void saveRole(String roleId, String childRoleIds) {
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthRole> dao = daoProvider.daoFor(NopAuthRole.class);
            if (dao.getEntityById(roleId) != null)
                return null;
            NopAuthRole role = dao.newEntity();
            role.setRoleId(roleId);
            role.setRoleName(roleId);
            role.setChildRoleIds(childRoleIds);
            dao.saveEntity(role);
            return null;
        });
    }

    private void saveUserWithRoles(String userId, String... roleIds) {
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthUser> userDao = daoProvider.daoFor(NopAuthUser.class);
            NopAuthUser user = userDao.newEntity();
            user.setUserId(userId);
            user.setUserName(userId);
            user.setNickName(userId);
            user.setPassword("x");
            user.setSalt("x");
            user.setOpenId(userId);
            user.setUserType(1);
            user.setStatus(1);
            user.setGender(1);
            user.setTenantId(TENANT_ID);
            userDao.saveEntity(user);

            for (String roleId : roleIds) {
                saveRole(roleId, null);
                IEntityDao<NopAuthUserRole> mappingDao = daoProvider.daoFor(NopAuthUserRole.class);
                NopAuthUserRole mapping = mappingDao.newEntity();
                mapping.setUserId(userId);
                mapping.setRoleId(roleId);
                mappingDao.saveEntity(mapping);
            }
            return null;
        });
    }

    private IServiceContext ctx(String userId, String userName) {
        ServiceContextImpl c = new ServiceContextImpl();
        UserContextImpl uc = new UserContextImpl();
        uc.setUserId(userId);
        uc.setUserName(userName);
        uc.setTenantId(TENANT_ID);
        c.setUserContext(uc);
        return c;
    }

    private IServiceContext adminCtx(String userId, String userName) {
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

    // ===================== H2 + ORM stack（TestMfaUserSelfService 同型） =====================

    private void buildH2Stack() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:role-mfa-policy-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MinimalBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("role-mfa-policy", CacheConfig.newConfig(100)));
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
