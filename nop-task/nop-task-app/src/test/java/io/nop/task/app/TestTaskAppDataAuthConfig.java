package io.nop.task.app;

import io.nop.api.core.auth.ISecurityContext;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.util.IVariableScope;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.core.model.DataAuthModel;
import io.nop.auth.core.model.ObjDataAuthModel;
import io.nop.auth.service.NopAuthConstants;
import io.nop.auth.service.auth.DefaultDataAuthChecker;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_DATA_AUTH_CONFIG_PATH;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 364 Phase 1 [04-02]：nop-task-app 的 data-auth 配置回归守卫。
 *
 * <p>背景：app.data-auth.xml 曾引用 2025-02 已改名的 {@code GenFromModules} 标签约 19 个月，
 * gen-extends 上下文 allowUnknownTag=true 导致静默产出空模型，DefaultDataAuthChecker 对空模型
 * fail-open——整个部署所有模块的行级数据权限失效且无任何测试信号（审计维度04-02/06-02）。
 *
 * <p>两层验证：
 * <ul>
 *   <li>parse 层：DslModelParser 能解析该文件且 gen-extends 展开后聚合到启用模块的规则
 *       （classpath 上 nop-auth 提供了 NopAuthUser 规则）；</li>
 *   <li>接线层：DefaultDataAuthChecker 经 nop.auth.data-auth-config-path 真实加载合并模型，
 *       对规则存在但角色不匹配的 bizObj 返回 false（fail-closed），而非空模型 fail-open 的 true。</li>
 * </ul>
 */
public class TestTaskAppDataAuthConfig {

    private static final String TASK_APP_DATA_AUTH_PATH = "/nop/task/auth/app.data-auth.xml";

    @BeforeAll
    public static void init() {
        // 只初始化到 IoC 之前（VFS/XLang/xlib 注册已就绪）：
        // 完整 initialize() 会启动 IoC 并触发 PageModelValidator 校验全部页面，
        // 而 nop-auth-web 的 NopAuthLoginAttempt/main.page.yaml 存在与本测试无关的页面缺陷，
        // 会导致初始化失败（见 daily log 2026-09-26 记录）。
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testDataAuthConfig_parsesAndMergesModuleObjs() {
        IResource resource = VirtualFileSystem.instance().getResource(TASK_APP_DATA_AUTH_PATH);
        assertTrue(resource.exists(), "data-auth config must exist: " + TASK_APP_DATA_AUTH_PATH);

        DataAuthModel model = (DataAuthModel) new DslModelParser().parseFromResource(resource);
        assertNotNull(model, "data-auth model must parse");

        // GenDataAuthFromModules 聚合所有启用模块的 data-auth 文件；
        // nop-auth 模块的 NopAuthUser 规则是 classpath 上必然存在的合并产物
        ObjDataAuthModel objAuth = model.getObj("NopAuthUser");
        assertNotNull(objAuth,
                "merged data-auth model must contain objs from enabled modules "
                        + "(broken gen tag silently produces an empty fail-open model)");
    }

    @Test
    public void testDataAuthChecker_loadedModelIsNotFailOpen() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_AUTH_DATA_AUTH_CONFIG_PATH, TASK_APP_DATA_AUTH_PATH);
        DefaultDataAuthChecker checker = new DefaultDataAuthChecker();
        try {
            checker.lazyInit();

            // data-auth 规则中的 ${$context.tenantId} 从 ContextProvider.currentContext() 解析
            IContext ctx = ContextProvider.getOrCreateContext();
            ctx.setTenantId("test-tenant");
            ctx.setUserName("test-user");

            ISecurityContext context = securityContext(userWithRole("test-role-none"));
            // NopAuthUser 的 default role-auth 对任意用户生效（isUserInRole 总是假定具有 user 角色），
            // 规则存在时 getFilter 必须生成 tenantId 行级过滤；
            // 若配置断裂退化为空模型 → objAuth==null → getFilter 返回 null（fail-open）
            ITreeBean filter = checker.getFilter("NopAuthUser", "query", context);
            assertNotNull(filter,
                    "getFilter must produce a row filter from merged module rules; "
                            + "null here means the checker silently loaded an empty fail-open model");
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_AUTH_DATA_AUTH_CONFIG_PATH,
                    NopAuthConstants.PATH_MAIN_DATA_AUTH);
            checker.destroy();
        }
    }

    private UserContextImpl userWithRole(String role) {
        UserContextImpl user = new UserContextImpl();
        user.setUserId("test-user");
        user.setUserName("test-user");
        user.setRoles(Collections.singleton(role));
        return user;
    }

    private ISecurityContext securityContext(IUserContext userContext) {
        return new ISecurityContext() {
            @Override
            public IVariableScope getEvalScope() {
                return null;
            }

            @Override
            public IUserContext getUserContext() {
                return userContext;
            }
        };
    }
}
