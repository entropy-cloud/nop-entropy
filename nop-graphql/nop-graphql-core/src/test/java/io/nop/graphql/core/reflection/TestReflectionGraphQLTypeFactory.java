package io.nop.graphql.core.reflection;

import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.core.reflect.hook.IPropGetMissingHook;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.schema.TypeRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestReflectionGraphQLTypeFactory {
    @Test
    public void testDynamicObjName() {
        TypeRegistry registry = new TypeRegistry();
        GraphQLType type = ReflectionGraphQLTypeFactory.INSTANCE.buildGraphQLType(ResultBean.class, registry, false);
        String source = registry.getType(type.getNamedTypeName()).toSource();
        System.out.println(source);
        assertTrue(source.contains("[MyObject]"));
    }

    public static class ResultBean {
        List<IPropGetMissingHook> list;

        @GraphQLReturn(bizObjName = "MyObject")
        public List<IPropGetMissingHook> getList() {
            return list;
        }
    }
}
