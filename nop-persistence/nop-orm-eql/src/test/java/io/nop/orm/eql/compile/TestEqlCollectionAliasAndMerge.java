package io.nop.orm.eql.compile;

import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmJoinOnModel;
import io.nop.orm.model.OrmToManyReferenceModel;
import io.nop.orm.model.OrmToOneReferenceModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1.6b（plan 2370）：簇 B 别名查重（P1-2.2-006）+ 簇 C 合并提升（P1-2.2-007）回归。
 *
 * <p>审计探针形态（_tmp/fcr-2.2-coll.md dim10b-03 / _tmp/fcr-2.2-coll-r2.md dim10b-11）：
 * ①用户别名 t1 + `_some` → 生成别名遮蔽外层，关联谓词退化为同表自比；②D1 形态
 * `_some.name='a' and (_some.userId='b' or o.name='x')` → unknown-alias[t1] 或静默篡改。
 */
public class TestEqlCollectionAliasAndMerge {

    @BeforeAll
    public static void init() {
        TestEqlCompileSql.init();
    }

    /**
     * 局部 context：复刻 relationContext 并给 AppRole 增加 name 列（原模型 AppRole 无 name
     * ——plan 审查 B2 实证；SILENT1 形态需要两实体同名列）。列必须在 init 前就位（init 后
     * 运行时加列不会重建内部数组）。
     */
    static TestEqlCompileSql.TestCompileContext relationContextWithRoleName() {
        OrmEntityModel user = new OrmEntityModel();
        user.setName("AppUser");
        user.setTableName("APP_USER");
        user.setQuerySpace("spaceA");
        OrmColumnModel userPk = TestEqlCompileSql.col("id", "SID", 1, true);
        OrmColumnModel userName = TestEqlCompileSql.col("name", "NAME", 2, false);
        OrmColumnModel userDept = TestEqlCompileSql.col("deptId", "DEPT_ID", 3, false);
        user.setColumns(new ArrayList<>(Arrays.asList(userPk, userName, userDept)));

        OrmEntityModel role = TestEqlCompileSql.entity("AppRole", "APP_ROLE", "spaceA",
                TestEqlCompileSql.col("id", "SID", 1, true),
                TestEqlCompileSql.col("userId", "USER_ID", 2, false),
                TestEqlCompileSql.col("name", "NAME", 3, false));
        OrmEntityModel dept = TestEqlCompileSql.entity("AppDept", "APP_DEPT", "spaceA",
                TestEqlCompileSql.col("id", "SID", 1, true), TestEqlCompileSql.col("name", "NAME", 2, false));

        OrmToManyReferenceModel roles = new OrmToManyReferenceModel();
        roles.setName("roles");
        roles.setRefEntityName("AppRole");
        roles.setRefEntityModel(role);
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

    private static ICompiledSql compile(String eql) {
        return TestEqlCompileSql.compile(relationContextWithRoleName(), eql);
    }

    private static String normalize(ICompiledSql compiled) {
        return compiled.getSql().getText().replaceAll("\\s+", " ");
    }

    /** 簇 B：用户别名 t1 + `_some`——生成别名必须避开 t1，关联条件不得退化为同表自比。 */
    @Test
    public void testUserAliasNotShadowedByGeneratedAlias() {
        String sql = normalize(compile("select t1.id from AppUser t1 where t1.roles._some.name = 'admin'"));
        // 审计探针（修复前）：exists 子查询同名 t1，(t1.SID = t1.USER_ID) 同表自比
        assertFalse(sql.contains("t1.SID = t1.USER_ID"), "关联条件不得自比：" + sql);
        assertTrue(sql.toLowerCase().contains("exists"), sql);
        assertTrue(sql.contains(".USER_ID"), sql);
    }

    /** 簇 C：D1 形态——跨 OR 分支合并后单 exists 含双条件，不再 unknown-alias[t1]。 */
    @Test
    public void testCrossBranchMergeCompiles() {
        String sql = normalize(compile(
                "select o.id from AppUser o where o.roles._some.name = 'a' and (o.roles._some.userId = 'b' or o.name = 'x')"));
        assertEquals(1, sql.split("(?i)exists").length - 1, "单 exists：" + sql);
        assertTrue(sql.contains("'a'"), sql);
        assertTrue(sql.contains("'b'"), "合并条件进入子查询：" + sql);
        assertFalse(sql.contains("unknown"), sql);
    }

    /** 簇 B×C 叠加（SILENT1）：t1 别名 + 同名列 + 跨分支合并——无恒真、无自比。 */
    @Test
    public void testSilentFormNoTautology() {
        String sql = normalize(compile(
                "select t1.id from AppUser t1 where t1.roles._some.name = 'a' and (t1.roles._some.userId = 'b' or t1.name = 'x')"));
        assertFalse(sql.contains("t1.SID = t1.USER_ID"), "关联条件不得自比：" + sql);
        assertFalse(sql.contains("t1.NAME = t1.NAME"), "不得产生恒真谓词：" + sql);
    }

    /** 簇 C：M1/M2 双 OR 对称形态——提升下沉后平铺合并行为保持（钉 SQL 形状，非语义等价断言）。 */
    @Test
    public void testSymmetricOrMergeShapePreserved() {
        String sql = normalize(compile(
                "select o.id from AppUser o where o.roles._some.name = 'a' or o.roles._some.userId = 'b'"));
        assertEquals(1, sql.split("(?i)exists").length - 1, "平铺 OR 合并为单 exists：" + sql);
    }

    @Test
    public void testFlatAndMergeShapePreserved() {
        String sql = normalize(compile(
                "select o.id from AppUser o where o.roles._some.name = 'a' and o.roles._some.userId = 'b'"));
        assertEquals(1, sql.split("(?i)exists").length - 1, "平铺 AND 合并为单 exists：" + sql);
    }

    /** 无集合操作符的普通查询别名行为不变。 */
    @Test
    public void testPlainQueryUnaffected() {
        String sql = normalize(compile("select o.name from AppUser o where o.dept.name = 'x'"));
        assertTrue(sql.contains("APP_DEPT"), sql);
        assertTrue(sql.toLowerCase().contains("join"), sql);
    }
}
