/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model.init;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmDomainModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_COL_DATA_TYPE_NOT_MATCH_DOMAIN_DEFINITION;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_COL_NO_STD_SQL_TYPE;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_DUPLICATE_PROP_ID;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_INVALID_COLUMN_DOMAIN;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_INVALID_PROP_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 覆盖 OrmEntityModelInitializer.initPropIds 与 OrmModelInitializer.syncDomains 的派生分支。
 * 这是全仓测试最薄的核心模块（91 main / 6 test）的补齐项，见 backlog orm-defect-audit-followups 遗留项 2。
 */
public class TestOrmEntityModelInitializerBasics {

    static OrmColumnModel column(String name, String code, int propId, StdSqlType sqlType, boolean primary) {
        OrmColumnModel col = new OrmColumnModel();
        col.setName(name);
        col.setCode(code);
        if (propId > 0)
            col.setPropId(propId);
        col.setStdSqlType(sqlType);
        col.setPrimary(primary);
        return col;
    }

    static OrmEntityModel entity(String name, String tableName, OrmColumnModel... cols) {
        OrmEntityModel m = new OrmEntityModel();
        m.setName(name);
        m.setTableName(tableName);
        List<OrmColumnModel> columns = new ArrayList<>();
        for (OrmColumnModel c : cols)
            columns.add(c);
        m.setColumns(columns);
        return m;
    }

    static OrmModel model(OrmEntityModel... entities) {
        OrmModel model = new OrmModel();
        model.setEntities(List.of(entities));
        return model;
    }

    static OrmDomainModel domain(String name, StdSqlType sqlType, Integer precision) {
        OrmDomainModel d = new OrmDomainModel();
        d.setName(name);
        d.setStdSqlType(sqlType);
        d.setPrecision(precision);
        return d;
    }

    // ---------------- propId 分配与稳定性（entity.xdef:86-89 的兼容承诺） ----------------

    /**
     * 删除中间列后，既有 propId 不重排；新增无 propId 的列分到 maxPropId+1 而非复用空洞。
     * 这正是 propId 稳定性承诺的语义：历史 JSON 差量 [[propId,old,new]] 不因删列而错位。
     */
    @Test
    public void testPropIdStableAfterColumnRemovalAndNoHoleReuse() {
        OrmEntityModel m = entity("A", "tbl_a",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                column("mid", "MID", 2, StdSqlType.VARCHAR, false),
                column("tail", "TAIL", 3, StdSqlType.VARCHAR, false));
        OrmModel model = model(m);
        model.init();
        assertEquals(3, m.getColumns().size());

        // 模拟删列后重新生成的 .orm.xml：保留列带原 propId（1、3），新增列不声明 propId。
        // 注意 OrmEntityModel.init 有 inited 短路（:351），重新初始化必须用全新实体，
        // 这也正是"重新生成的模型"在真实链路中的形态
        OrmEntityModel m2 = entity("A", "tbl_a",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                column("tail", "TAIL", 3, StdSqlType.VARCHAR, false),
                column("newCol", "NEW_COL", 0, StdSqlType.VARCHAR, false));
        model(m2).init();

        assertEquals(1, m2.getColumn("sid").getPropId());
        assertEquals(3, m2.getColumn("tail").getPropId());
        // 新列拿到 maxPropId+1=4，而不是复用空洞 2 —— propId 稳定性的核心承诺
        assertEquals(4, m2.getColumn("newCol").getPropId());
        // init 后按 propId 排序
        assertEquals(List.of("sid", "tail", "newCol"),
                m2.getColumns().stream().map(OrmColumnModel::getName).toList());
    }

    /** 未声明 propId 的列按声明顺序从 maxPropId+1 连续分配 */
    @Test
    public void testImplicitPropIdSequentialAssignment() {
        OrmEntityModel m = entity("A", "tbl_a",
                column("sid", "SID", 5, StdSqlType.VARCHAR, true),
                column("b", "B", 0, StdSqlType.VARCHAR, false),
                column("c", "C", 0, StdSqlType.VARCHAR, false));
        model(m).init();
        assertEquals(6, m.getColumn("b").getPropId());
        assertEquals(7, m.getColumn("c").getPropId());
    }

    @Test
    public void testDuplicatePropIdThrowsWithContext() {
        OrmEntityModel m = entity("A", "tbl_a",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                column("dup", "DUP", 1, StdSqlType.VARCHAR, false));
        NopException e = assertThrows(NopException.class, () -> model(m).init());
        assertEquals(ERR_ORM_MODEL_DUPLICATE_PROP_ID.getErrorCode(), e.getErrorCode());
        assertEquals("A", e.getParam("entityName"));
        assertEquals(1, e.getParam("propId"));
    }

    @Test
    public void testPropIdOverMaxThrows() {
        OrmColumnModel col = column("bad", "BAD", Integer.MAX_VALUE, StdSqlType.VARCHAR, false);
        OrmEntityModel m = entity("A", "tbl_a", col);
        NopException e = assertThrows(NopException.class, () -> model(m).init());
        assertEquals(ERR_ORM_MODEL_INVALID_PROP_ID.getErrorCode(), e.getErrorCode());
    }

    // ---------------- deleteFlagProp 自动同化（logical-deletion.md 方案 A 的锁定测试） ----------------

    /**
     * 只声明 deleteVersionProp 时自动同化 deleteFlagProp 指向同一列。
     * 这是 docs-for-ai/02-core-guides/logical-deletion.md 方案 A（推荐）的实现基础，
     * 此前曾被误判为缺陷（见 analysis 更正记录），本测试锁定其语义防止误改。
     */
    @Test
    public void testDeleteFlagPropAutoUnifiedFromDeleteVersionProp() {
        OrmColumnModel delVersion = column("delVersion", "DEL_VERSION", 2, StdSqlType.BIGINT, false);
        OrmEntityModel m = entity("A", "tbl_a",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                delVersion);
        m.setDeleteVersionProp("delVersion");
        assertNull(m.getDeleteFlagProp());

        model(m).init();

        // 自动同化：两个 prop 指向同一列（方案 A 允许且推荐）
        assertEquals("delVersion", m.getDeleteFlagProp());
        assertEquals(delVersion.getPropId(), m.getDeleteFlagPropId());
        assertEquals(delVersion.getPropId(), m.getDeleteVersionPropId());
    }

    /** 显式声明 deleteFlagProp 时不被覆盖 */
    @Test
    public void testExplicitDeleteFlagPropNotOverridden() {
        OrmColumnModel flag = column("deleted", "DELETED", 2, StdSqlType.BOOLEAN, false);
        OrmColumnModel ver = column("delVersion", "DEL_VERSION", 3, StdSqlType.BIGINT, false);
        OrmEntityModel m = entity("A", "tbl_a",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                flag, ver);
        m.setDeleteFlagProp("deleted");
        m.setDeleteVersionProp("delVersion");
        model(m).init();
        assertEquals(flag.getPropId(), m.getDeleteFlagPropId());
        assertEquals(ver.getPropId(), m.getDeleteVersionPropId());
    }

    // ---------------- domain 同步（syncDomains 分支） ----------------

    @Test
    public void testUnknownDomainThrowsWithAllowedNames() {
        OrmEntityModel m = entity("A", "tbl_a",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                column("x", "X", 0, StdSqlType.VARCHAR, false));
        m.getColumn("x").setDomain("no-such-domain");

        OrmModel model = model(m);
        NopException e = assertThrows(NopException.class, model::init);
        assertEquals(ERR_ORM_MODEL_INVALID_COLUMN_DOMAIN.getErrorCode(), e.getErrorCode());
        assertEquals("no-such-domain", e.getParam("domain"));
    }

    /** stdDataType 与 domain 定义冲突时抛错（不是静默覆盖） */
    @Test
    public void testDomainDataTypeConflictThrows() {
        OrmColumnModel col = column("x", "X", 0, StdSqlType.INTEGER, false);
        OrmEntityModel m = entity("A", "tbl_a",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true), col);

        OrmDomainModel d = new OrmDomainModel();
        d.setName("ver");
        d.setStdSqlType(StdSqlType.BIGINT);
        d.setStdDataType(io.nop.commons.type.StdDataType.LONG);

        OrmColumnModel c = m.getColumn("x");
        c.setDomain("ver");
        c.setStdDataType(io.nop.commons.type.StdDataType.STRING); // 与 domain 的 LONG 冲突

        OrmModel model = model(m);
        model.setDomains(List.of(d));
        NopException e = assertThrows(NopException.class, model::init);
        assertEquals(ERR_ORM_MODEL_COL_DATA_TYPE_NOT_MATCH_DOMAIN_DEFINITION.getErrorCode(), e.getErrorCode());
    }

    /** 精度合并：列未声明 precision 时继承 domain 的，已声明时保留自己的 */
    @Test
    public void testDomainPrecisionInheritedOnlyWhenAbsent() {
        OrmEntityModel m = entity("A", "tbl_a",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                column("a", "A", 0, StdSqlType.VARCHAR, false),
                column("b", "B", 0, StdSqlType.VARCHAR, false));
        m.getColumn("a").setDomain("name50");
        m.getColumn("b").setDomain("name50");
        m.getColumn("b").setPrecision(20); // 显式优先

        OrmDomainModel d = domain("name50", StdSqlType.VARCHAR, 50);
        OrmModel model = model(m);
        model.setDomains(List.of(d));
        model.init();

        assertEquals(50, m.getColumn("a").getPrecision()); // 继承
        assertEquals(20, m.getColumn("b").getPrecision()); // 显式保留
        // domain 的 stdSqlType 同步到列
        assertEquals(StdSqlType.VARCHAR, m.getColumn("a").getStdSqlType());
    }

    /** 列与 domain 都没有 stdSqlType 时抛错；仅 domain 有则继承 */
    @Test
    public void testStdSqlTypeRequiredOrInheritedFromDomain() {
        // 无 domain 且未声明 stdSqlType
        OrmColumnModel bare = new OrmColumnModel();
        bare.setName("bare");
        bare.setCode("BARE");
        bare.setPropId(1);
        bare.setPrimary(true);
        OrmEntityModel m1 = entity("A", "tbl_a", bare);
        NopException e = assertThrows(NopException.class, () -> model(m1).init());
        assertEquals(ERR_ORM_MODEL_COL_NO_STD_SQL_TYPE.getErrorCode(), e.getErrorCode());

        // domain 提供 stdSqlType + stdDataType 推导
        OrmEntityModel m2 = entity("B", "tbl_b",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                column("v", "V", 0, null, false));
        OrmColumnModel v = m2.getColumn("v");
        v.setStdSqlType(null);
        v.setDomain("ver");
        OrmDomainModel d = domain("ver", StdSqlType.BIGINT, null);
        OrmModel model2 = model(m2);
        model2.setDomains(List.of(d));
        model2.init();
        assertEquals(StdSqlType.BIGINT, v.getStdSqlType());
        assertEquals(io.nop.commons.type.StdDataType.LONG, v.getStdDataType());
    }

    /** stdDataType 未声明时由 stdSqlType 自动推导 */
    @Test
    public void testStdDataTypeDerivedFromSqlType() {
        OrmEntityModel m = entity("A", "tbl_a",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                column("n", "N", 0, StdSqlType.INTEGER, false));
        model(m).init();
        assertEquals(io.nop.commons.type.StdDataType.INT, m.getColumn("n").getStdDataType());
    }

    /** init 后 ownerEntityModel 反向引用被正确设置（binder 数组构建依赖它） */
    @Test
    public void testColumnOwnerBackReference() {
        OrmEntityModel m = entity("A", "tbl_a",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true));
        model(m).init();
        assertSame(m, m.getColumn("sid").getOwnerEntityModel());
    }
}
