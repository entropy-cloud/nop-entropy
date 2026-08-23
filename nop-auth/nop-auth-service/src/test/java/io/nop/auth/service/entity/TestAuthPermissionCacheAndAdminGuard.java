package io.nop.auth.service.entity;

import io.nop.api.core.auth.IRolePermissionMapping;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.messages.SiteMapBean;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.core.sitemap.ISiteMapProvider;
import io.nop.auth.dao.entity.NopAuthRoleDataAuth;
import io.nop.auth.dao.entity.NopAuthRoleResource;
import io.nop.auth.service.NopAuthConstants;
import io.nop.auth.service.auth.DefaultDataAuthChecker;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * check 审计 nop-auth 报告三条权限时效/越权条目的回归：
 * <ul>
 *   <li>[P1] 表模式数据权限行变更后 DataAuthModel 缓存必须清理（原永不生效）；</li>
 *   <li>[P2] 角色-资源授权关系变更后 sitemap 权限缓存必须刷新（原最长滞留10分钟）；</li>
 *   <li>[P2] resetUserPassword/enableUser/disableUser 必须有运行时 admin 校验
 *       （原仅靠操作权限+租户数据权限，权限下放即同租户任意账号接管）。</li>
 * </ul>
 */
public class TestAuthPermissionCacheAndAdminGuard extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static class RecordingDataAuthChecker extends DefaultDataAuthChecker {
        int clears;

        @Override
        public void clearCache() {
            clears++;
        }
    }

    static class RecordingSiteMapProvider implements ISiteMapProvider {
        int refreshes;

        @Override
        public void refreshCache() {
            refreshes++;
        }

        @Override
        public SiteMapBean getSiteMap(String siteId, String locale) {
            return null;
        }

        @Override
        public Set<String> getAllowedSiteEntries(String siteId, String userId, String deptId, Set<String> roleIds) {
            return Set.of();
        }

        @Override
        public SiteMapBean filterAllowedMenu(SiteMapBean site, String userId, String deptId,
                                             Set<String> roleIds, boolean includeFunctionPoints) {
            return site;
        }

        @Override
        public List<SiteMapBean> loadStaticSiteMap() {
            return List.of();
        }

        @Override
        public Set<String> getRolesWithPermission(Set<String> permissions) {
            return Set.of();
        }
    }

    /** afterEntityChange为protected，经同包wrapper触发。 */
    static class WrappingRoleDataAuthBizModel extends NopAuthRoleDataAuthBizModel {
        void fireAfterEntityChange(NopAuthRoleDataAuth entity, String action) {
            afterEntityChange(entity, action, new ServiceContextImpl());
        }
    }

    static class WrappingRoleResourceBizModel extends NopAuthRoleResourceBizModel {
        void fireAfterEntityChange(NopAuthRoleResource entity, String action) {
            afterEntityChange(entity, action, new ServiceContextImpl());
        }
    }

    @Test
    public void testRoleDataAuthChangeClearsDataAuthModelCache() {
        WrappingRoleDataAuthBizModel bizModel = new WrappingRoleDataAuthBizModel();
        RecordingDataAuthChecker checker = new RecordingDataAuthChecker();
        bizModel.dataAuthChecker = checker;

        bizModel.fireAfterEntityChange(new NopAuthRoleDataAuth(), "update");
        assertEquals(1, checker.clears, "data auth row change must clear DataAuthModel cache");

        bizModel.fireAfterEntityChange(new NopAuthRoleDataAuth(), "delete");
        assertEquals(2, checker.clears, "each change must clear again (no latching)");
    }

    @Test
    public void testRoleDataAuthChangeWithoutCheckerIsNoop() {
        WrappingRoleDataAuthBizModel bizModel = new WrappingRoleDataAuthBizModel();
        // 手工装配未注入checker时不抛异常（@Nullable语义）
        bizModel.fireAfterEntityChange(new NopAuthRoleDataAuth(), "update");
    }

    @Test
    public void testRoleResourceChangeRefreshesSiteMapCache() {
        WrappingRoleResourceBizModel bizModel = new WrappingRoleResourceBizModel();
        RecordingSiteMapProvider provider = new RecordingSiteMapProvider();
        bizModel.siteMapProvider = provider;

        bizModel.fireAfterEntityChange(new NopAuthRoleResource(), "save");
        assertEquals(1, provider.refreshes, "role-resource grant change must refresh sitemap cache");

        bizModel.fireAfterEntityChange(new NopAuthRoleResource(), "delete");
        assertEquals(2, provider.refreshes);
    }

    private static ServiceContextImpl contextWithRoles(String... roles) {
        UserContextImpl uc = new UserContextImpl();
        uc.setUserId("user-1");
        uc.setUserName("user-1");
        uc.setRoles(Set.of(roles));
        ServiceContextImpl ctx = new ServiceContextImpl();
        ctx.setUserContext(uc);
        return ctx;
    }

    @Test
    public void testResetUserPasswordRequiresAdmin() {
        NopAuthUserBizModel bizModel = new NopAuthUserBizModel();
        NopException err = assertThrows(NopException.class,
                () -> bizModel.resetUserPassword("victim", "NewPassword1!", contextWithRoles("user")));
        assertEquals(ERR_AUTH_INVALID_LOGIN_REQUEST.getErrorCode(), err.getErrorCode(),
                "non-admin must be rejected before any dao access (privilege escalation guard)");
    }

    @Test
    public void testDisableUserRequiresAdmin() {
        NopAuthUserBizModel bizModel = new NopAuthUserBizModel();
        assertThrows(NopException.class,
                () -> bizModel.disableUser("victim", contextWithRoles("user")));
    }

    @Test
    public void testEnableUserRequiresAdmin() {
        NopAuthUserBizModel bizModel = new NopAuthUserBizModel();
        assertThrows(NopException.class,
                () -> bizModel.enableUser("victim", contextWithRoles("helpdesk")));
    }

    /** 对照组：admin角色通过requireAdmin门禁（进入后续dao流程，无ORM装配时以dao层异常失败，而非门禁拒绝）。 */
    @Test
    public void testAdminPassesRequireAdminGuard() {
        NopAuthUserBizModel bizModel = new NopAuthUserBizModel();
        try {
            bizModel.enableUser("some-user", contextWithRoles(NopAuthConstants.ROLE_ADMIN));
        } catch (NopException e) {
            if (ERR_AUTH_INVALID_LOGIN_REQUEST.getErrorCode().equals(e.getErrorCode())
                    && e.getParam("msg") != null && String.valueOf(e.getParam("msg")).contains("admin")) {
                fail("admin must pass requireAdmin guard");
            }
        } catch (RuntimeException e) {
            // 无dao装配的裸BizModel在this.get()处失败（如bizObjectManager未注入的NPE）——
            // 说明已越过requireAdmin门禁，对照组目的达成
        }
    }
}
