package io.nop.graphql.core.reflection;

import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.auth.ActionAuthMeta;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.schema.TypeRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-2.6-007 修复回归（plan 2378 R1.12 Phase 2）：
 * @BizLoader 方法上的 @Auth 此前是死注解（Auth 全仓唯一消费点在 buildActionField 的
 * query/mutation 路径，loader 路径结构性无法携带权限）。修复后 buildFetcherField 消费
 * 显式 @Auth；无注解的 loader 保持 auth=null（不补缺省权限——loader 是字段装载器，
 * 缺省回填过严，收敛裁定见 plan 2378）。
 */
public class TestBizLoaderAuthAssembly {

    @BizModel("TestLoaderAuthSvc")
    public static class AuthedLoaderBizModel {
        @BizLoader
        @Auth(roles = "admin", permissions = "TestLoaderAuthSvc:authedField")
        public List<String> authedField() {
            return List.of("a");
        }

        @BizLoader
        public List<String> openField() {
            return List.of("b");
        }
    }

    @Test
    public void testAuthedLoaderCarriesAuthAfterBuild() {
        GraphQLBizModel bizModel = ReflectionBizModelBuilder.INSTANCE.build(new AuthedLoaderBizModel(),
                new TypeRegistry(), new GraphQLBizModels());

        GraphQLFieldDefinition authed = bizModel.getLoaders().get("authedField");
        assertNotNull(authed, "authedField loader must be registered");

        ActionAuthMeta auth = authed.getAuth();
        assertNotNull(auth, "@Auth on @BizLoader must be assembled into field auth (P1-2.6-007)");
        assertNotNull(auth.getRoles());
        assertTrue(auth.getRoles().contains("admin"), "loader auth roles should contain admin");
        assertEquals("TestLoaderAuthSvc:authedField", auth.getPermissions().toString(),
                "loader auth permissions must mirror the annotation declaration");
    }

    @Test
    public void testLoaderWithoutAuthStaysNull() {
        GraphQLBizModel bizModel = ReflectionBizModelBuilder.INSTANCE.build(new AuthedLoaderBizModel(),
                new TypeRegistry(), new GraphQLBizModels());

        GraphQLFieldDefinition open = bizModel.getLoaders().get("openField");
        assertNotNull(open, "openField loader must be registered");
        assertNull(open.getAuth(),
                "loader without @Auth must keep auth=null (no default backfill for loaders, plan 2378)");
    }
}
