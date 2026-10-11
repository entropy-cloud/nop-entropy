package io.nop.graphql.core.engine;

import io.nop.api.core.auth.ActionAuthMeta;
import io.nop.api.core.auth.IActionAuthChecker;
import io.nop.api.core.ApiConfigs;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.MultiCsvSet;
import io.nop.auth.api.AuthApiErrors;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.api.core.auth.IUserContext;
import io.nop.graphql.core.GraphQLErrors;

import java.lang.reflect.Proxy;
import java.util.Collection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-2.6 fail-open 三处收敛行为测试（plan 2378 R1.12 Phase 3）：
 * <p>
 * 三配置组合矩阵（默认 false，零兼容影响）：
 * <ul>
 * <li>默认：fail-open 语义保持——enable-action-auth=true 无 checker 仅 WARN（F-API2-1）；auth==null allow；auth!=null 无 checker allow</li>
 * <li>require-auth-checker=true：请求入口 fail-fast（ERR_GRAPHQL_NO_ACTION_AUTH_CHECKER）；auth 声明+无 checker 旁路 fail-closed</li>
 * <li>strict-action-auth=true：auth==null 按 deny，错误信息 null 安全（不 NPE）</li>
 * </ul>
 * 含 1 个无注解 loader 字段 strict deny 样例（plan 2378 Phase 2 F4 裁定的行为侧验证）。
 */
public class TestGraphQLActionAuthStrictModes {

    private static final IActionAuthChecker DENY_CHECKER = (permissions, securityContext) -> false;

    private Boolean oldRequire;
    private Boolean oldStrict;

    @BeforeEach
    public void setUp() {
        oldRequire = ApiConfigs.CFG_AUTH_REQUIRE_AUTH_CHECKER.get();
        oldStrict = ApiConfigs.CFG_AUTH_STRICT_ACTION_AUTH.get();
    }

    @AfterEach
    public void tearDown() {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_AUTH_REQUIRE_AUTH_CHECKER, oldRequire);
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_AUTH_STRICT_ACTION_AUTH, oldStrict);
    }

    // ==================== 处 1：请求入口 fail-fast ====================

    @Test
    public void testDefaultFailOpenPreserved() {
        GraphQLEngine engine = new GraphQLEngine();
        engine.setEnableActionAuth(true);
        engine.setActionAuthChecker(null);

        IServiceContext ctx = new ServiceContextImpl();
        // 默认（require=false）：不抛异常，仅 WARN
        engine.newGraphQLContextFromContext(ctx);
        assertTrue(engine.actionAuthNoCheckerWarned, "default mode must keep F-API2-1 warn semantics");
    }

    @Test
    public void testRequireAuthCheckerFailsFastAtContextCreation() {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_AUTH_REQUIRE_AUTH_CHECKER, true);
        GraphQLEngine engine = new GraphQLEngine();
        engine.setEnableActionAuth(true);
        engine.setActionAuthChecker(null);

        NopException e = assertThrows(NopException.class,
                () -> engine.newGraphQLContextFromContext(new ServiceContextImpl()),
                "require-auth-checker=true must fail fast at request entry when checker is absent");
        assertEquals(GraphQLErrors.ERR_GRAPHQL_NO_ACTION_AUTH_CHECKER.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testRequireAuthCheckerNoImpactWhenCheckerPresent() {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_AUTH_REQUIRE_AUTH_CHECKER, true);
        GraphQLEngine engine = new GraphQLEngine();
        engine.setEnableActionAuth(true);
        engine.setActionAuthChecker(DENY_CHECKER);

        engine.newGraphQLContextFromContext(new ServiceContextImpl());
    }

    @Test
    public void testRequireAuthCheckerNoImpactWhenActionAuthDisabled() {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_AUTH_REQUIRE_AUTH_CHECKER, true);
        GraphQLEngine engine = new GraphQLEngine();
        engine.setEnableActionAuth(false);
        engine.setActionAuthChecker(null);

        engine.newGraphQLContextFromContext(new ServiceContextImpl());
    }

    // ==================== 处 2：strict auth==null deny ====================

    @Test
    public void testDefaultAuthNullAllowsAccess() {
        IServiceContext ctx = new ServiceContextImpl();
        ctx.setActionAuthChecker(DENY_CHECKER);
        assertTrue(GraphQLActionAuthChecker.isAllowAccess(null, ctx),
                "default: auth==null means allow (explicit-declaration model)");
    }

    @Test
    public void testStrictModeDeniesNullAuth() {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_AUTH_STRICT_ACTION_AUTH, true);
        IServiceContext ctx = new ServiceContextImpl();
        ctx.setActionAuthChecker(DENY_CHECKER);
        assertFalse(GraphQLActionAuthChecker.isAllowAccess(null, ctx),
                "strict mode: auth==null must deny");
    }

    @Test
    public void testStrictModeNoImpactWithoutChecker() {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_AUTH_STRICT_ACTION_AUTH, true);
        IServiceContext ctx = new ServiceContextImpl();
        // 无 checker：fail-open 语义保持（strict 只在 checker 在位时生效）
        assertTrue(GraphQLActionAuthChecker.isAllowAccess(null, ctx),
                "strict mode must not deny when no checker is registered (checker-presence gate)");
    }

    /**
     * 无注解 loader 字段 strict deny 样例（F4 群体裁定的行为侧验证）：
     * auth==null 的字段在 strict=true + checker 在位时按 deny，
     * checkAuth 抛错路径 null 安全（不得 NPE）。
     */
    @Test
    public void testStrictDenyNoAnnotationLoaderFieldNullSafe() {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_AUTH_STRICT_ACTION_AUTH, true);
        IServiceContext ctx = new ServiceContextImpl();
        ctx.setActionAuthChecker(DENY_CHECKER);
        ctx.setUserContext(newUser("u1", "user"));

        NopException e = assertThrows(NopException.class,
                () -> GraphQLActionAuthChecker.checkAuth("TestObj", "openField", null,
                        DENY_CHECKER, ctx, false),
                "strict deny for auth==null field must surface as ERR_AUTH_NO_PERMISSION_FOR_FIELD");
        assertEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION_FOR_FIELD.getErrorCode(), e.getErrorCode());
    }

    // ==================== 处 3：auth 声明 + 无 checker 旁路 fail-closed ====================

    @Test
    public void testDefaultAuthDeclaredNoCheckerStillAllows() {
        IServiceContext ctx = new ServiceContextImpl();
        ActionAuthMeta auth = new ActionAuthMeta(false, CollectionHelper.buildImmutableSet("admin"),
                MultiCsvSet.fromText("Obj:query"), false);
        assertTrue(GraphQLActionAuthChecker.isAllowAccess(auth, ctx),
                "default: auth declared but no checker keeps platform fail-open semantics");
    }

    @Test
    public void testRequireModeFailClosedForDeclaredAuthWithoutChecker() {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_AUTH_REQUIRE_AUTH_CHECKER, true);
        IServiceContext ctx = new ServiceContextImpl();
        ctx.setUserContext(newUser("u1", "admin"));
        ActionAuthMeta auth = new ActionAuthMeta(false, CollectionHelper.buildImmutableSet("admin"),
                MultiCsvSet.fromText("Obj:query"), false);

        assertFalse(GraphQLActionAuthChecker.isAllowAccess(auth, ctx),
                "require mode: declared auth without checker must fail closed");

        NopException e = assertThrows(NopException.class,
                () -> GraphQLActionAuthChecker.checkAuth("TestObj", "approvedAction", auth,
                        null, ctx, true),
                "require mode bypass (DefaultBizAuthChecker shape) must reject with ERR_AUTH_NO_PERMISSION");
        assertEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode(), e.getErrorCode());
    }

    /**
     * IUserContext 动态代理：nop-auth-core 的 UserContextImpl 不在 graphql-core 类路径上，
     * 测试只需 getUserId/isUserInRole/isUserInAnyRole/getRoles 语义。
     */
    @SuppressWarnings("unchecked")
    private IUserContext newUser(String userId, String... roles) {
        java.util.Set<String> roleSet = CollectionHelper.buildImmutableSet(roles);
        return (IUserContext) Proxy.newProxyInstance(IUserContext.class.getClassLoader(),
                new Class[]{IUserContext.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUserId":
                        case "getUserName":
                        case "getNickName":
                            return userId;
                        case "getRoles":
                            return roleSet;
                        case "isUserInRole":
                            return roleSet.contains(args[0]);
                        case "isUserInAnyRole":
                            return !java.util.Collections.disjoint(roleSet, (Collection<String>) args[0]);
                        default:
                            return null;
                    }
                });
    }

}
