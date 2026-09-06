/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLFieldSelection;
import io.nop.graphql.core.ast.GraphQLSelectionSet;
import io.nop.graphql.core.utils.GraphQLTypeHelper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * schema声明为列表类型但fetcher返回数组/非Collection值时的行为：
 * Object[]按Arrays.asList适配继续装载；其余非集合形态抛带字段名的
 * ERR_GRAPHQL_FIELD_LIST_VALUE_NOT_COLLECTION，而非无上下文的ClassCastException。
 */
public class TestGraphQLExecutorFetchNext {

    private static final class ConstantFetcher implements io.nop.graphql.core.IDataFetcher {
        private final Object value;

        ConstantFetcher(Object value) {
            this.value = value;
        }

        @Override
        public Object get(io.nop.graphql.core.IDataFetchingEnvironment env) {
            return value;
        }
    }

    /**
     * 构造list类型字段 + 单个子字段（子字段fetcher返回固定值）的env。
     */
    private static DataFetchingEnvironment envForListField(String fieldName) {
        GraphQLFieldDefinition subField = new GraphQLFieldDefinition();
        subField.setName("name");
        subField.setType(GraphQLTypeHelper.scalarType(io.nop.graphql.core.schema.GraphQLScalarType.String));
        subField.setFetcher(new ConstantFetcher("v"));

        GraphQLFieldSelection subSelection = new GraphQLFieldSelection();
        subSelection.setName("name");
        subSelection.setFieldDefinition(subField);

        GraphQLSelectionSet selectionSet = new GraphQLSelectionSet();
        selectionSet.setSelections(java.util.List.of(subSelection));

        GraphQLFieldDefinition listField = new GraphQLFieldDefinition();
        listField.setName(fieldName);
        listField.setType(GraphQLTypeHelper.listType(
                GraphQLTypeHelper.scalarType(io.nop.graphql.core.schema.GraphQLScalarType.String)));

        GraphQLFieldSelection listSel = new GraphQLFieldSelection();
        listSel.setName(fieldName);
        listSel.setFieldDefinition(listField);
        listSel.setSelectionSet(selectionSet);

        DataFetchingEnvironment env = new DataFetchingEnvironment();
        env.setSelection(listSel);
        // fetchList装载子字段时按alias从selectionBean取子选择
        env.setSelectionBean(io.nop.api.core.beans.FieldSelectionBean.fromProp("name"));
        return env;
    }

    private static GraphQLExecutor executor() {
        return new GraphQLExecutor(null, null, null, null);
    }

    /**
     * 数组返回值被适配为列表装载：修复前直接(Collection<?>)强转抛CCE。
     */
    @Test
    public void testArrayValueAdaptedToList() {
        DataFetchingEnvironment env = envForListField("items");
        Object result = executor().fetchNext(new String[]{"a", "b"}, env);

        assertTrue(result instanceof List, "array value must be adapted and loaded as a list");
        List<?> list = (List<?>) result;
        assertEquals(2, list.size());
        Map<String, Object> first = (Map<String, Object>) list.get(0);
        assertEquals("v", first.get("name"));
    }

    /**
     * 非数组非集合返回值抛带字段名的明确错误：修复前无上下文CCE。
     */
    @Test
    public void testNonCollectionValueThrowsFieldNameError() {
        DataFetchingEnvironment env = envForListField("items");

        NopException err = assertThrows(NopException.class,
                () -> executor().fetchNext("not-a-collection", env));
        assertEquals("nop.err.graphql.field-list-value-not-collection", err.getErrorCode());
        assertEquals("items", err.getParam("fieldName"));
        assertEquals(String.class.getName(), err.getParam("actualType"));
    }
}
