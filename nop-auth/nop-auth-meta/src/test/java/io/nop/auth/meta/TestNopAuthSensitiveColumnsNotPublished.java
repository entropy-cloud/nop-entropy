package io.nop.auth.meta;

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
 * check 审计 [P2] 会话表敏感令牌列缺少 masked/not-pub 标记：NopAuthSession.accessToken/refreshToken
 * 是可换发凭证的直接会话接管原语，不得经 GraphQL 出参发布。
 * <p>
 * 结构性防线（对齐 NopAuthUser.password / NopAuthMfaSetting.secret 的 W3/W4 双源模式）：
 * ORM 源列 {@code tagSet="masked,not-pub"} 驱动生成的 base xmeta 标记 {@code published=false}；
 * {@code ObjMetaToGraphQLDefinition} 按 merged xmeta 构建 GraphQL schema，
 * {@code published=false} == 不出现在发布的 GraphQL 类型中。
 */
public class TestNopAuthSensitiveColumnsNotPublished {

    private static final String MERGED_XMETA = "/nop/auth/model/NopAuthSession/NopAuthSession.xmeta";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static IObjPropMeta loadProp(String prop) {
        IObjMeta meta = (IObjMeta) ResourceComponentManager.instance().loadComponentModel(MERGED_XMETA);
        IObjPropMeta p = meta.getProp(prop);
        assertNotNull(p, prop + " prop must exist in " + MERGED_XMETA);
        return p;
    }

    @Test
    public void testSessionTokensNotPublished() {
        assertFalse(loadProp("accessToken").isPublished(), "session accessToken must not be published");
        assertFalse(loadProp("refreshToken").isPublished(), "session refreshToken must not be published");
    }

    @Test
    public void testSessionTokensMasked() {
        assertTrue(loadProp("accessToken").containsTag("masked"),
                "session accessToken must be masked (SQL param log masking)");
        assertTrue(loadProp("refreshToken").containsTag("masked"), "session refreshToken must be masked");
    }

    /** 非敏感字段保持发布，防止误伤会话管理控制台的常规查询。 */
    @Test
    public void testNormalSessionPropsRemainExposed() {
        assertTrue(loadProp("sessionId").isPublished(), "sessionId must stay published");
        assertTrue(loadProp("loginAddr").isPublished(), "loginAddr must stay published");
    }
}
