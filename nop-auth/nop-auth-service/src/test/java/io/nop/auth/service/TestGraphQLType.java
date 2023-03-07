/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.core.Description;
import io.nop.auth.api.messages.LoginResult;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.service.biz.LoginApiBizModel;
import io.nop.auth.service.entity.NopAuthUserBizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.utils.JavaGenericTypeHelper;
import io.nop.graphql.core.ast.GraphQLDefinition;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.reflection.GraphQLBizModel;
import io.nop.graphql.core.reflection.ReflectionBizModelBuilder;
import io.nop.graphql.core.schema.TypeRegistry;
import io.nop.graphql.core.utils.GraphQLNameHelper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGraphQLType {
    @Description("根据Java类型自动生成GraphQLType")
    @Test
    public void testJavaType() {
        TypeRegistry registry = new TypeRegistry();
        ReflectionBizModelBuilder.INSTANCE.build(new LoginApiBizModel(), new HashMap<>(), registry);
        String typeName = GraphQLNameHelper.getResultTypeName(LoginResult.class);
        GraphQLDefinition def = registry.getType(typeName);
        assertNotNull(def);
    }

    @Test
    public void testJavaGenericReflection() {
        // File projectDir = MavenDirHelper.projectDir(TestGraphQLType.class);
        // new GenAopProxy().execute(projectDir,false);

        IClassModel classModel = ReflectionManager.instance().getClassModel(NopAuthUserBizModel.class);
        IFunctionModel fn = classModel.getMethod("get", 3);
        assertEquals(NopAuthUser.class, fn.getReturnClass());

        Method[] methods = NopAuthUserBizModel.class.getMethods();
        for (Method method : methods) {
            if (method.getName().equals("findFirst")) {
                ParameterizedType type = (ParameterizedType) JavaGenericTypeHelper
                        .getSupertype(NopAuthUserBizModel.class, NopAuthUserBizModel.class, CrudBizModel.class);
                TypeVariable<?> var = (TypeVariable<?>) method.getGenericReturnType();
                TypeVariable<?>[] vars = CrudBizModel.class.getTypeParameters();
                assertTrue(vars[0] == var);
                assertEquals(NopAuthUser.class, type.getActualTypeArguments()[0]);
            }
        }
    }

    @Test
    public void testPageBean() {
        TypeRegistry registry = new TypeRegistry();
        GraphQLBizModel bizModel = ReflectionBizModelBuilder.INSTANCE.build(new NopAuthUserBizModel(), new HashMap<>(),
                registry);
        GraphQLFieldDefinition field = bizModel.getOperationDefinition(GraphQLOperationType.query, "findPage");
        assertEquals("PageBean_NopAuthUser", field.getType().toString());
    }
}
