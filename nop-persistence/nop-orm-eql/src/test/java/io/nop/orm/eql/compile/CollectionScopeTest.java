package io.nop.orm.eql.compile;

import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.enums.SqlCollectionOperator;
import io.nop.orm.eql.utils.EqlASTBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CollectionScopeTest {

    @Test
    public void testBuildSingleOperator() {
        // 模拟创建SqlColumnName对象
        SqlColumnName colName = createMockColumnName("o.roles._some.status");

        CollectionScope scope = CollectionScope.build(colName);

        assertNotNull(scope);
        assertEquals(SqlCollectionOperator.SOME, scope.getOperator());
        assertEquals("o.roles", scope.getCollectionProp());
        assertEquals("o.roles._some.", scope.getCollectionPrefix());
        assertEquals(colName, scope.getColNameNode());

        // 检查是否为叶子节点
        assertTrue(scope.isLeaf());
        assertEquals("status", scope.getFinalProperty());

        // 检查没有子scope
        assertTrue(scope.getChildren().isEmpty());
    }

    @Test
    public void testBuildNestedOperators() {
        // 测试嵌套操作符：o.roles._some.depts._all.status
        SqlColumnName colName = createMockColumnName("o.roles._some.depts._all.status");

        CollectionScope scope = CollectionScope.build(colName);

        assertNotNull(scope);
        assertEquals(SqlCollectionOperator.SOME, scope.getOperator());
        assertEquals("o.roles", scope.getCollectionProp());
        assertEquals("o.roles._some.", scope.getCollectionPrefix());

        // 检查有子scope
        assertFalse(scope.isLeaf());
        assertEquals(1, scope.getChildren().size());

        // 获取子scope
        CollectionScope childScope = scope.getChildren().get("depts._all.");
        assertNotNull(childScope);
        assertEquals(SqlCollectionOperator.ALL, childScope.getOperator());
        assertEquals("depts", childScope.getCollectionProp());
        assertEquals("depts._all.", childScope.getCollectionPrefix());

        // 检查子scope的父关系
        assertEquals(scope, childScope.getParent());

        // 检查子scope为叶子节点
        assertTrue(childScope.isLeaf());
        assertEquals("status", childScope.getFinalProperty());

        // 检查子scope没有子scope
        assertTrue(childScope.getChildren().isEmpty());
    }

    private SqlColumnName createMockColumnName(String fullName) {
        return EqlASTBuilder.colName(fullName);
    }
}