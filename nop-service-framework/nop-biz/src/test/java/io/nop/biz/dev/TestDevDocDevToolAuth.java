package io.nop.biz.dev;

import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.auth.ActionAuthMeta;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.reflection.GraphQLBizModel;
import io.nop.graphql.core.reflection.GraphQLBizModels;
import io.nop.graphql.core.reflection.ReflectionBizModelBuilder;
import io.nop.graphql.core.schema.TypeRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G5-13-01：DevDoc/DevTool 是debug部署下暴露的内省与破坏性维护面（导出IoC容器XML、全部配置值、
 * 全量schema、清空全局缓存），平台语义下 auth==null 即公开访问（GraphQLActionAuthChecker 对
 * auth==null 返回true），因此全部公开操作必须要求admin角色，对齐同包DevStatBizModel先例。
 * <p>
 * 修复前：两个BizModel的全部方法均无@Auth注解，本测试在修复前失败（先红后绿）。
 */
public class TestDevDocDevToolAuth {

    @Test
    public void testAllDevDocOperationsRequireAdminRole() {
        List<String> bizOps = publicBizOperations(DevDocBizModel.class);
        assertTrue(bizOps.size() >= 7, "DevDocBizModel should expose at least 7 biz operations, found: " + bizOps);
        assertAllRequireAdmin(DevDocBizModel.class, bizOps);
    }

    @Test
    public void testAllDevToolOperationsRequireAdminRole() {
        List<String> bizOps = publicBizOperations(DevToolBizModel.class);
        assertEquals(2, bizOps.size(), "DevToolBizModel should expose exactly 2 biz mutations: " + bizOps);
        assertAllRequireAdmin(DevToolBizModel.class, bizOps);
    }

    /**
     * P1-2.6-007 修复回归（plan 2378 R1.12 Phase 2）：@Auth 在 @BizLoader methods 上此前是死注解
     * （buildFetcherField 不消费 Auth，loader 路径结构性无法携带权限）。升级断言：
     * 经 ReflectionBizModelBuilder 构建后，字段级 auth 装配到位（roles=admin）——
     * 从「注解存在」升级为「装配生效」。methods 的 forType=GlobalVariableDefBean，
     * loader 字段注册在 GraphQLBizModels 的 GlobalVariableDefBean 分模型下。
     */
    @Test
    public void testDevDocLoaderAuthAssembledIntoGraphQLDefinition() {
        TypeRegistry registry = new TypeRegistry();
        ReflectionBizModelBuilder.INSTANCE.build(new DevDocBizModel(), registry, new GraphQLBizModels());

        // methods 的 forType=GlobalVariableDefBean（非 biz 对象）：buildFetcherField 产出的字段
        // 经 getLoaderForType merge 进 TypeRegistry 的对应类型定义（类型名沿
        // getGraphQLTypeName 的 g_<fqn> 合成规则）
        String typeName = io.nop.graphql.core.utils.GraphQLNameHelper
                .getGraphQLTypeName(io.nop.biz.dev.beans.GlobalVariableDefBean.class, false);
        io.nop.graphql.core.ast.GraphQLTypeDefinition typeDef = registry.getType(typeName);
        assertNotNull(typeDef, typeName + " type must be registered");

        GraphQLFieldDefinition methods = null;
        for (GraphQLFieldDefinition field : ((io.nop.graphql.core.ast.GraphQLObjectDefinition) typeDef).getFields()) {
            if ("methods".equals(field.getName())) {
                methods = field;
                break;
            }
        }
        assertNotNull(methods, "methods loader field must be registered on GlobalVariableDefBean");

        ActionAuthMeta auth = methods.getAuth();
        assertNotNull(auth, "@Auth on @BizLoader methods must be assembled into field auth (P1-2.6-007)");
        assertNotNull(auth.getRoles());
        assertTrue(auth.getRoles().contains("admin"),
                "methods loader auth roles should contain admin, actual: " + auth.getRoles());
    }

    private static List<String> publicBizOperations(Class<?> clazz) {
        List<String> names = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()))
                continue;
            if (method.isAnnotationPresent(BizQuery.class) || method.isAnnotationPresent(BizMutation.class)
                    || method.isAnnotationPresent(BizLoader.class)) {
                names.add(method.getName());
            }
        }
        return names;
    }

    private static void assertAllRequireAdmin(Class<?> clazz, List<String> opNames) {
        for (String name : opNames) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.getName().equals(name) || !Modifier.isPublic(method.getModifiers()))
                    continue;
                Auth auth = method.getAnnotation(Auth.class);
                assertNotNull(auth, clazz.getSimpleName() + "." + method.getName() + " must declare @Auth");
                assertEquals("admin", auth.roles(),
                        clazz.getSimpleName() + "." + method.getName() + " must require admin role");
            }
        }
    }
}
