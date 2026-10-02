package io.nop.auth.api;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * nop-biz-auth-api 结构契约：对外暴露的 LoginApi/SiteMapApi BizModel 接口
 * 必须保持操作面（操作名 + query/mutation 分类）稳定——这是前后端与网关路由的契约面，
 * 破坏性变更必须在此显式失败。
 */
public class TestAuthApiContract {

    /**
     * LoginApi 以 @BizModel("LoginApi") 暴露，登录/登出/刷新/MFA/验证码操作齐全，
     * 且 query/mutation 分类正确（getLoginResult 与 getLoginUserInfo 是查询）。
     */
    @Test
    public void testLoginApiExposesStableOperationContract() {
        BizModel bizModel = LoginApi.class.getAnnotation(BizModel.class);
        assertNotNull(bizModel, "LoginApi must be annotated with @BizModel");
        assertEquals("LoginApi", bizModel.value());

        Set<String> mutations = new HashSet<>(java.util.List.of(
                "login", "logout", "refreshToken", "mfaVerify", "sendSmsCode", "sendMfaCode",
                "loginAsync", "logoutAsync"));
        Set<String> queries = new HashSet<>(java.util.List.of("getLoginResult", "getLoginUserInfo"));

        for (Method method : LoginApi.class.getMethods()) {
            if (method.isAnnotationPresent(BizMutation.class)) {
                assertTrue(mutations.contains(method.getName()),
                        "unexpected mutation on LoginApi: " + method.getName());
                mutations.remove(method.getName());
            }
            if (method.isAnnotationPresent(BizQuery.class)) {
                assertTrue(queries.contains(method.getName()),
                        "unexpected query on LoginApi: " + method.getName());
                queries.remove(method.getName());
            }
        }
        assertTrue(mutations.isEmpty(), "LoginApi is missing mutations: " + mutations);
        assertTrue(queries.isEmpty(), "LoginApi is missing queries: " + queries);
    }

    /**
     * SiteMapApi 暴露菜单树查询操作，返回 ApiRequest<SiteMapBean>（前端菜单渲染契约）。
     */
    @Test
    public void testSiteMapApiExposesSiteMapOperation() throws Exception {
        Method getSiteMap = SiteMapApi.class.getMethod("getSiteMap", io.nop.api.core.beans.ApiRequest.class);
        assertNotNull(getSiteMap);

        java.lang.reflect.Type returnType = getSiteMap.getGenericReturnType();
        assertTrue(returnType instanceof java.lang.reflect.ParameterizedType,
                "getSiteMap must return a generic ApiRequest type");
        java.lang.reflect.Type[] typeArgs =
                ((java.lang.reflect.ParameterizedType) returnType).getActualTypeArguments();
        assertEquals(io.nop.auth.api.messages.SiteMapBean.class, typeArgs[0],
                "getSiteMap payload type must be SiteMapBean");
    }

    /**
     * 错误码契约：核心鉴权错误码字符串必须保持稳定（客户端按码做 i18n 与跳转）。
     */
    @Test
    public void testAuthApiErrorCodesAreStable() {
        assertEquals("nop.err.auth.no-permission", AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode());
        assertEquals("nop.err.auth.no-user-context", AuthApiErrors.ERR_AUTH_NO_USER_CONTEXT.getErrorCode());
        assertEquals("nop.err.auth.no-data-auth", AuthApiErrors.ERR_AUTH_NO_DATA_AUTH.getErrorCode());
        assertEquals("nop.err.auth.no-role", AuthApiErrors.ERR_AUTH_NO_ROLE.getErrorCode());

        assertNotNull(AuthApiErrors.ERR_AUTH_NO_PERMISSION.getDescription());
        assertNotNull(AuthApiErrors.ERR_AUTH_NO_DATA_AUTH.getDescription());
    }

    /**
     * 状态/登出/登录类型常量契约（DB 与 API 侧共享的数值语义）。
     */
    @Test
    public void testAuthApiConstantsContract() {
        assertEquals(0, AuthApiConstants.USER_STATUS_DISABLED);
        assertEquals(1, AuthApiConstants.USER_STATUS_ACTIVE);
        assertEquals(2, AuthApiConstants.USER_STATUS_SUSPENDED);
        assertEquals(3, AuthApiConstants.USER_STATUS_ABANDONED);

        assertEquals(3, AuthApiConstants.LOGOUT_TYPE_KILL);
        assertEquals(4, AuthApiConstants.LOGOUT_TYPE_RELOGIN);

        assertEquals(1, AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD);
        assertEquals(4, AuthApiConstants.LOGIN_TYPE_SSO);
        assertEquals(5, AuthApiConstants.LOGIN_TYPE_PHONE_SMS);

        assertEquals("main", AuthApiConstants.SITE_MAIN);
    }
}
