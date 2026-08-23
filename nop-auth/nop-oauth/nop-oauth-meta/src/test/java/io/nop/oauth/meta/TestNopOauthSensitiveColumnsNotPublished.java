package io.nop.oauth.meta;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check 审计 [P2] OAuth 授权表/客户端表敏感令牌列缺少 masked/not-pub 标记：
 * authorizationCode/accessToken/refreshToken/oidcIdToken/state 可直接仿冒客户端与接管会话，
 * clientSecret 是客户端凭证（masked,var,not-pub——对照 NopAuthUser.password 只写不读形态）。
 * ORM 源列 tagSet 驱动生成的 base xmeta 标记 published=false == 不出现在发布的 GraphQL 类型中。
 */
public class TestNopOauthSensitiveColumnsNotPublished {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static IObjPropMeta loadProp(String model, String prop) {
        IObjMeta meta = (IObjMeta) ResourceComponentManager.instance().loadComponentModel(model);
        IObjPropMeta p = meta.getProp(prop);
        assertNotNull(p, prop + " prop must exist in " + model);
        return p;
    }

    @Test
    public void testAuthorizationTokenValuesNotPublished() {
        String model = "/nop/oauth/model/NopOauthAuthorization/NopOauthAuthorization.xmeta";
        assertFalse(loadProp(model, "state").isPublished(), "authorization state must not be published");
        assertFalse(loadProp(model, "authorizationCodeValue").isPublished(), "authorizationCodeValue must not be published");
        assertFalse(loadProp(model, "accessTokenValue").isPublished(), "accessTokenValue must not be published");
        assertFalse(loadProp(model, "refreshTokenValue").isPublished(), "refreshTokenValue must not be published");
        assertFalse(loadProp(model, "oidcIdTokenValue").isPublished(), "oidcIdTokenValue must not be published");
    }

    @Test
    public void testClientSecretWriteOnly() {
        String model = "/nop/oauth/model/NopOauthRegisteredClient/NopOauthRegisteredClient.xmeta";
        IObjPropMeta secret = loadProp(model, "clientSecret");
        assertFalse(secret.isPublished(), "clientSecret must not be published");
        assertTrue(secret.isInsertable(), "clientSecret must remain insertable (admin registers client with secret)");
    }

    /** 非敏感字段保持发布，防止误伤授权管理控制台的常规查询。 */
    @Test
    public void testNormalOauthPropsRemainExposed() {
        String model = "/nop/oauth/model/NopOauthAuthorization/NopOauthAuthorization.xmeta";
        assertTrue(loadProp(model, "principalName").isPublished(), "principalName must stay published");
        assertTrue(loadProp(model, "authorizedScopes").isPublished(), "authorizedScopes must stay published");
    }
}
