package io.nop.biz.crud;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.commons.type.StdSqlType;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.impl.ObjMetaImpl;
import io.nop.xlang.xmeta.impl.ObjPropMetaImpl;
import io.nop.xlang.xmeta.impl.ObjTreeModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 树查询CTE SQL的逻辑删除过滤：启用逻辑删除的实体在锚点段(b)与递归段(o)都必须追加deleteFlag条件，
 * 否则已删除节点进入tree_page后，findListForTree经batchGet装载实体会因orm_logicalDeleted整体抛异常。
 */
public class TestTreeEntityHelper {

    @Test
    public void testTreeSqlFiltersLogicalDeletedOnBothSegments() {
        IObjMeta objMeta = treeObjMeta();
        SQL sql = TreeEntityHelper.buildTreeEntitySql(objMeta, entityModel(true, StdSqlType.INTEGER), null).end();

        String text = sql.getText();
        // 修复前：手工拼的CTE完全没有deleteFlag条件
        assertTrue(text.contains("b.delFlag = ?"), "anchor segment must filter deleteFlag: " + text);
        assertTrue(text.contains("o.delFlag = ?"), "recursive segment must filter deleteFlag: " + text);
    }

    @Test
    public void testCountSqlFiltersLogicalDeleted() {
        IObjMeta objMeta = treeObjMeta();
        SQL sql = TreeEntityHelper.buildTreeEntityCountSql(objMeta, entityModel(true, StdSqlType.INTEGER), null).end();

        assertTrue(sql.getText().contains("b.delFlag = ?"), "count sql must filter deleteFlag: " + sql.getText());
        assertTrue(sql.getText().contains("o.delFlag = ?"));
    }

    @Test
    public void testNoLogicalDeleteConditionWhenDisabled() {
        IObjMeta objMeta = treeObjMeta();
        // 未启用逻辑删除的实体保持原有行为；实体模型未注册（null）时不过滤
        SQL sql = TreeEntityHelper.buildTreeEntitySql(objMeta, entityModel(false, StdSqlType.INTEGER), null).end();
        assertFalse(sql.getText().contains("delFlag"));

        SQL nullModel = TreeEntityHelper.buildTreeEntitySql(objMeta, null, null).end();
        assertFalse(nullModel.getText().contains("delFlag"));
    }

    @Test
    public void testFilterAndDeleteFlagChainedByAnd() {
        IObjMeta objMeta = treeObjMeta();
        TreeBean filter = FilterBeans.eq("name", "a");
        SQL sql = TreeEntityHelper.buildTreeEntitySql(objMeta, entityModel(true, StdSqlType.INTEGER), filter).end();

        String text = sql.getText();
        // deleteFlag、用户filter、根节点条件之间必须以and连接，不能出现"where and"或两个条件粘连
        int delFlagPos = text.indexOf("b.delFlag = ?");
        int filterPos = text.indexOf("b.name=?");
        int rootPos = text.indexOf("b.parentId is null");
        assertTrue(delFlagPos >= 0 && filterPos > delFlagPos && rootPos > filterPos,
                "conditions must be chained in order: " + text);
        assertFalse(text.contains("where  and"), text);
        assertFalse(text.contains("?and"), text);
    }

    @Test
    public void testNotDeletedValueByColumnType() {
        // 与GenSqlHelper.getBooleanLiteral取值语义一致
        assertEquals(0, TreeEntityHelper.notDeletedValue(entityModel(true, StdSqlType.INTEGER)));
        assertEquals(Boolean.FALSE, TreeEntityHelper.notDeletedValue(entityModel(true, StdSqlType.BOOLEAN)));
        assertEquals("0", TreeEntityHelper.notDeletedValue(entityModel(true, StdSqlType.VARCHAR)));
    }

    // ==================== fixture ====================

    static IObjMeta treeObjMeta() {
        ObjMetaImpl objMeta = new ObjMetaImpl();
        objMeta.setName("TestTreeEntity");
        objMeta.setPrimaryKey(Collections.singleton("id"));

        ObjTreeModel tree = new ObjTreeModel();
        tree.setParentProp("parentId");
        objMeta.setTree(tree);

        objMeta.addProp(plainProp("parentId"));
        return objMeta;
    }

    static ObjPropMetaImpl plainProp(String name) {
        ObjPropMetaImpl prop = new ObjPropMetaImpl();
        prop.setName(name);
        return prop;
    }

    static IEntityModel entityModel(boolean useLogicalDelete, StdSqlType deleteFlagSqlType) {
        return (IEntityModel) Proxy.newProxyInstance(
                TestTreeEntityHelper.class.getClassLoader(),
                new Class[]{IEntityModel.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isUseLogicalDelete":
                            return useLogicalDelete;
                        case "getDeleteFlagProp":
                            return useLogicalDelete ? "delFlag" : null;
                        case "getDeleteFlagPropId":
                            return 2;
                        case "getColumnByPropId":
                            if (Integer.valueOf(2).equals(args[0]))
                                return deleteFlagColumn(deleteFlagSqlType);
                            return null;
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    static IColumnModel deleteFlagColumn(StdSqlType sqlType) {
        return (IColumnModel) Proxy.newProxyInstance(
                TestTreeEntityHelper.class.getClassLoader(),
                new Class[]{IColumnModel.class},
                (proxy, method, args) -> {
                    if ("getStdSqlType".equals(method.getName()))
                        return sqlType;
                    return defaultValue(method.getReturnType());
                });
    }

    static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive())
            return null;
        if (type == boolean.class)
            return false;
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        if (type == double.class)
            return 0D;
        if (type == float.class)
            return 0F;
        if (type == short.class)
            return (short) 0;
        if (type == byte.class)
            return (byte) 0;
        if (type == char.class)
            return (char) 0;
        return null;
    }
}
