package io.nop.code.service.entity;

import io.nop.api.core.auth.ActionAuthMeta;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.service.api.dto.SymbolDTO;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.reflection.GraphQLBizModels;
import io.nop.graphql.core.reflection.ReflectionBizModelBuilder;
import io.nop.graphql.core.schema.TypeRegistry;
import io.nop.graphql.core.utils.GraphQLNameHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-2.6-007 修复回归（plan 2378 R1.12 Phase 2）：
 * nop-code 六个生产 @BizLoader（NopCodeFileBizModel×4 + NopCodeSymbolBizModel×2）声明的
 * @Auth 此前是死注解（buildFetcherField 不消费）。修复后经 ReflectionBizModelBuilder 构建，
 * 对应 loader 字段的 auth 必须装配到位（permissions 与注解一致）。
 * 带注解 loader 构建断言（plan 2378 审查 F7：不能以既有测试零回归替代装配证据）。
 */
public class TestNopCodeLoaderAuthAssembly {

    @Test
    public void testNopCodeFileLoadersCarryAuthAfterBuild() {
        TypeRegistry registry = new TypeRegistry();
        ReflectionBizModelBuilder.INSTANCE.build(new NopCodeFileBizModel(), registry, new GraphQLBizModels());

        String forTypeName = GraphQLNameHelper.getGraphQLTypeName(CodeFileAnalysisResult.class, false);
        assertLoaderAuth(registry, forTypeName, "NopCodeFile:query", "symbols", "types", "sourceCode", "outline");
    }

    @Test
    public void testNopCodeSymbolLoadersCarryAuthAfterBuild() {
        TypeRegistry registry = new TypeRegistry();
        ReflectionBizModelBuilder.INSTANCE.build(new NopCodeSymbolBizModel(), registry, new GraphQLBizModels());

        String forTypeName = GraphQLNameHelper.getGraphQLTypeName(SymbolDTO.class, false);
        assertLoaderAuth(registry, forTypeName, "NopCodeSymbol:query", "usages");
    }

    /**
     * forType 指向非 biz 对象的 loader 字段经 getLoaderForType merge 进 TypeRegistry 中
     * forType 对应的类型定义（类型名沿 getGraphQLTypeName 的 g_&lt;fqn&gt; 合成规则）。
     * 只在该类型定义上断言，避免与其它 DTO 的同名 bean 属性字段混淆。
     */
    private void assertLoaderAuth(TypeRegistry registry, String forTypeName, String expectedPermission,
                                  String... loaderNames) {
        io.nop.graphql.core.ast.GraphQLTypeDefinition typeDef = registry.getType(forTypeName);
        assertNotNull(typeDef, forTypeName + " type must be registered");
        GraphQLObjectDefinition objDef = (GraphQLObjectDefinition) typeDef;
        assertNotNull(objDef.getFields(), forTypeName + " must have fields");

        for (String loaderName : loaderNames) {
            GraphQLFieldDefinition loaderField = null;
            for (GraphQLFieldDefinition field : objDef.getFields()) {
                if (loaderName.equals(field.getName())) {
                    loaderField = field;
                    break;
                }
            }
            assertNotNull(loaderField, "loader field " + loaderName + " must be registered on " + forTypeName);

            ActionAuthMeta auth = loaderField.getAuth();
            assertNotNull(auth, "loader field " + loaderName + " must carry @Auth after build (P1-2.6-007)");
            assertNotNull(auth.getPermissions(), "loader field " + loaderName + " must declare permissions");
            assertTrue(auth.getPermissions().toString().contains(expectedPermission),
                    "loader field " + loaderName + " permissions should contain " + expectedPermission
                            + ", actual: " + auth.getPermissions());
        }
    }
}
