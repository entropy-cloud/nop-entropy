package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectFeatures;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmJoinOnModel;
import io.nop.orm.model.OrmToManyReferenceModel;
import io.nop.orm.model.OrmToOneReferenceModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_COLLECTION_OPERATOR_NOT_ALLOWED_HERE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DML_PROP_JOIN_NOT_SUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1.6a（plan 2369）：簇 D DML prop-join 渲染缺口 fail-fast 回归（P1-2.2-001/002/004/005/008/010）
 * + P1-2.2-003 NOT(_all) 语义核销。
 *
 * <p>修复语义：集合操作符只允许出现在 select 语句的 where 中（transform 前置改写）；到达
 * resolvePropPath 的操作符段一律编译期响亮失败。update/delete 目标表起自目标 source 的多段
 * 属性路径（where/相关子查询外层别名/SET 赋值目标/RETURNING 投影）一律响亮失败；单段 owner
 * （渲染层物理表名回退）与子查询自身作用域路径（left≠目标 source）不受影响。
 */
public class TestEqlDmlPropJoinFailFast {

    static IDialect dialect;

    @BeforeAll
    public static void init() {
        // 复用 TestEqlCompileSql 的最小测试方言（含 returning 支持与 upper 函数）
        TestEqlCompileSql.init();
        dialect = TestEqlCompileSql.dialect;
    }

    /** 复刻 TestEqlCompileSql.relationContext()，roles 增加 keyProp=id（nop-wf/nop-dyn 真实形态）。 */
    static TestEqlCompileSql.TestCompileContext keyPropRelationContext() {
        OrmEntityModel user = new OrmEntityModel();
        user.setName("AppUser");
        user.setTableName("APP_USER");
        user.setQuerySpace("spaceA");
        OrmColumnModel userPk = TestEqlCompileSql.col("id", "SID", 1, true);
        OrmColumnModel userName = TestEqlCompileSql.col("name", "NAME", 2, false);
        OrmColumnModel userDept = TestEqlCompileSql.col("deptId", "DEPT_ID", 3, false);
        user.setColumns(new ArrayList<>(java.util.Arrays.asList(userPk, userName, userDept)));

        OrmEntityModel role = TestEqlCompileSql.entity("AppRole", "APP_ROLE", "spaceA",
                TestEqlCompileSql.col("id", "SID", 1, true), TestEqlCompileSql.col("userId", "USER_ID", 2, false));
        OrmEntityModel dept = TestEqlCompileSql.entity("AppDept", "APP_DEPT", "spaceA",
                TestEqlCompileSql.col("id", "SID", 1, true), TestEqlCompileSql.col("name", "NAME", 2, false));

        OrmToManyReferenceModel roles = new OrmToManyReferenceModel();
        roles.setName("roles");
        roles.setRefEntityName("AppRole");
        roles.setRefEntityModel(role);
        roles.setKeyProp("id");
        roles.setColumns(Collections.emptyList());
        OrmJoinOnModel roleJoin = new OrmJoinOnModel();
        roleJoin.setLeftPropModel(userPk);
        roleJoin.setRightPropModel(role.getColumn("userId", false));
        roles.setJoin(new ArrayList<>(Collections.singletonList(roleJoin)));
        user.addProp(roles);

        OrmToOneReferenceModel deptRef = new OrmToOneReferenceModel();
        deptRef.setName("dept");
        deptRef.setRefEntityName("AppDept");
        deptRef.setRefEntityModel(dept);
        deptRef.setColumns(Collections.singletonList(userDept));
        OrmJoinOnModel deptJoin = new OrmJoinOnModel();
        deptJoin.setLeftPropModel(userDept);
        deptJoin.setRightPropModel(dept.getColumn("id", false));
        deptRef.setJoin(new ArrayList<>(Collections.singletonList(deptJoin)));
        user.addProp(deptRef);

        user.init();

        TestEqlCompileSql.TestCompileContext ctx = new TestEqlCompileSql.TestCompileContext();
        ctx.entities.put("AppUser", user);
        ctx.entities.put("AppRole", role);
        ctx.entities.put("AppDept", dept);
        return ctx;
    }

    private static void assertOperatorNotAllowed(String eql) {
        TestEqlCompileSql.TestCompileContext ctx = keyPropRelationContext();
        NopException e = assertThrows(NopException.class, () -> TestEqlCompileSql.compile(ctx, eql));
        assertEquals(ERR_EQL_COLLECTION_OPERATOR_NOT_ALLOWED_HERE.getErrorCode(), e.getErrorCode());
    }

    private static void assertDmlPropJoinNotSupported(String eql) {
        TestEqlCompileSql.TestCompileContext ctx = TestEqlCompileSql.relationContext();
        NopException e = assertThrows(NopException.class, () -> TestEqlCompileSql.compile(ctx, eql));
        assertEquals(ERR_EQL_DML_PROP_JOIN_NOT_SUPPORTED.getErrorCode(), e.getErrorCode());
    }

    // ===== 守卫 1：操作符位置矩阵（P1-2.2-001/002/004） =====

    @Test
    public void testOperatorInUpdateWhereFailsFast() {
        assertOperatorNotAllowed("update AppUser o set o.name = 'x' where o.roles._some.userId = 'y'");
    }

    @Test
    public void testOperatorInDeleteWhereFailsFast() {
        assertOperatorNotAllowed("delete from AppUser o where o.roles._all.userId = 'y'");
    }

    @Test
    public void testOperatorInOrderByFailsFast() {
        assertOperatorNotAllowed("select o.name from AppUser o order by o.roles._some.userId");
    }

    @Test
    public void testOperatorInHavingFailsFast() {
        assertOperatorNotAllowed(
                "select o.name from AppUser o group by o.name having count(o.roles._some.userId) > 1");
    }

    @Test
    public void testOperatorInProjectionFailsFast() {
        assertOperatorNotAllowed("select o.roles._some.userId from AppUser o");
    }

    @Test
    public void testOperatorInGroupByFailsFast() {
        assertOperatorNotAllowed("select o.name from AppUser o group by o.roles._some.userId");
    }

    // ===== 守卫 2：DML 多段属性路径四入口（P1-2.2-008/005/010） =====

    @Test
    public void testPropJoinInUpdateWhereFailsFast() {
        assertDmlPropJoinNotSupported("update AppUser o set o.name = 'x' where o.dept.name = 'y'");
    }

    @Test
    public void testPropJoinInDeleteWhereFailsFast() {
        assertDmlPropJoinNotSupported("delete from AppUser o where o.dept.name = 'y'");
    }

    @Test
    public void testPropJoinInCorrelatedSubqueryFailsFast() {
        assertDmlPropJoinNotSupported(
                "delete from AppUser o where exists (select 1 from AppDept d where d.id = o.dept.id)");
    }

    @Test
    public void testPropJoinInSetTargetFailsFast() {
        assertDmlPropJoinNotSupported("update AppUser o set o.dept.name = 'x' where o.id = '1'");
    }

    @Test
    public void testPropJoinInReturningFailsFast() {
        assertDmlPropJoinNotSupported(
                "update AppUser o set o.name = 'x' where o.id = '1' returning o.dept.name");
    }

    // ===== 合法面回归：不受守卫影响 =====

    @Test
    public void testSingleSegmentOwnerUpdateStillLegal() {
        TestEqlCompileSql.TestCompileContext ctx = TestEqlCompileSql.relationContext();
        ICompiledSql compiled = TestEqlCompileSql.compile(ctx, "update AppUser o set o.name = 'x' where o.id = '1'");
        String sql = compiled.getSql().getText().replaceAll("\\s+", " ");
        assertTrue(sql.contains("update APP_USER"), sql);
        assertTrue(sql.contains("SID = '1'"), "单段 owner 渲染为无限定列：" + sql);
    }

    @Test
    public void testSelfScopePathInCorrelatedSubqueryStillLegal() {
        TestEqlCompileSql.TestCompileContext ctx = TestEqlCompileSql.relationContext();
        ICompiledSql compiled = TestEqlCompileSql.compile(ctx,
                "delete from AppUser o where exists (select 1 from AppDept d where d.id = o.id)");
        String sql = compiled.getSql().getText().replaceAll("\\s+", " ");
        // 单段外层引用 o.id 经渲染层物理表名回退（dim10c-16 P9 合法形态），不得被守卫误伤
        assertTrue(sql.contains("exists"), sql);
        assertTrue(sql.contains("APP_T_USER".toLowerCase()) || sql.contains("APP_USER"),
                "单段外层引用以物理表名限定：" + sql);
    }

    @Test
    public void testSelectWhereOperatorStillWorks() {
        TestEqlCompileSql.TestCompileContext ctx = keyPropRelationContext();
        ICompiledSql compiled = TestEqlCompileSql.compile(ctx,
                "select o.name from AppUser o where o.roles._some.userId = 'y'");
        String sql = compiled.getSql().getText().replaceAll("\\s+", " ");
        assertTrue(sql.toLowerCase().contains("exists"), sql);
    }

    // ===== P1-2.2-003 核销：NOT(_all) 语义回归 =====

    @Test
    public void testNotAllCompilesWithCorrectSemantics() {
        TestEqlCompileSql.TestCompileContext ctx = TestEqlCompileSql.relationContext();
        ICompiledSql compiled = TestEqlCompileSql.compile(ctx,
                "select o.name from AppUser o where not (o.roles._all.userId = 'x')");
        String sql = compiled.getSql().getText().replaceAll("\\s+", " ");
        // plan 2320 语义：¬∀(P)=∃(¬P) → exists 且叶条件取反（EQ→NE），不得崩溃或语义反转
        assertTrue(sql.toLowerCase().contains("exists"), "NOT _all 编译为 exists 形态：" + sql);
        assertTrue(sql.contains("<>") || sql.contains("!="), "叶条件取反 EQ→NE：" + sql);
    }
}
