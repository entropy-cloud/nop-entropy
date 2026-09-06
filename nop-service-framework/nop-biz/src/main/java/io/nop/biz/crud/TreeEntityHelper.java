package io.nop.biz.crud;

import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.BizConstants;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.dao.DaoQueryHelper;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.impl.ObjTreeModel;

import static io.nop.biz.BizErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.biz.BizErrors.ERR_BIZ_NOT_SUPPORT_TREE_MODEL;
import static io.nop.biz.BizErrors.ERR_BIZ_TREE_ENTITY_NO_PARENT_PROP;

public class TreeEntityHelper {
    public static SQL.SqlBuilder buildTreeEntityCountSql(IObjMeta objMeta, IEntityModel entityModel, ITreeBean filter) {
        SQL.SqlBuilder sb = SQL.begin();
        buildTreeEntityBaseSql(sb, objMeta, entityModel, filter);
        sb.sql("\n select count(1) from tree_page t");
        return sb;
    }

    public static SQL.SqlBuilder buildTreeEntitySql(IObjMeta objMeta, IEntityModel entityModel, ITreeBean filter) {
        SQL.SqlBuilder sb = SQL.begin();
        buildTreeEntityBaseSql(sb, objMeta, entityModel, filter);
        String pkProp = objMeta.getPkProp();
        String sortProp = objMeta.getTree().getSortProp();
        sb.sql("\nselect t.id, t.displayName, t.parentId, t.level, t.joinId");
        sb.sql("\nfrom tree_page t");
        sb.sql("\norder by t.");
        if (sortProp != null) {
            sb.append("sortProp");
        } else {
            sb.append("id");
        }
        return sb;
    }

    private static void buildTreeEntityBaseSql(SQL.SqlBuilder sb, IObjMeta objMeta, IEntityModel entityModel,
                                               ITreeBean filter) {
        ObjTreeModel treeModel = objMeta.getTree();
        if (treeModel == null)
            throw new NopException(ERR_BIZ_NOT_SUPPORT_TREE_MODEL)
                    .param(ARG_BIZ_OBJ_NAME, objMeta.getName());

        String entityName = objMeta.getEntityName();
        if (entityName == null)
            entityName = objMeta.getName();

        String pkProp = objMeta.getPkProp();

        String dispProp = objMeta.getDisplayProp();
        if (dispProp == null)
            dispProp = pkProp;

        String levelProp = treeModel.getLevelProp();
        String parentProp = treeModel.getParentProp();
        String rootParentValue = treeModel.getRootParentValue();
        String rootLevelValue = treeModel.getRootLevelValue();
        String sortProp = treeModel.getSortProp();

        if (StringHelper.isEmpty(parentProp))
            throw new NopException(ERR_BIZ_TREE_ENTITY_NO_PARENT_PROP)
                    .param(ARG_BIZ_OBJ_NAME, objMeta.getName());

        IObjPropMeta propMeta = objMeta.requireProp(parentProp);
        IObjPropMeta relMeta = BizObjMetaHelper.getRelationMeta(objMeta, propMeta);
        String leftJoinProp = (String) relMeta.prop_get(BizConstants.EXT_JOIN_LEFT_PROP);
        String rightJoinProp = (String) relMeta.prop_get(BizConstants.EXT_JOIN_RIGHT_PROP);
        if (leftJoinProp == null)
            leftJoinProp = parentProp;
        if (rightJoinProp == null)
            rightJoinProp = pkProp;

        sb.sql("with recursive tree_page as (\n");
        appendTreeSql(sb, "b", entityName, pkProp, dispProp, parentProp, levelProp, sortProp, rightJoinProp);
        sb.where();
        // 手工拼CTE不会经过GenSqlHelper的自动逻辑删除过滤，这里需要显式追加deleteFlag条件，
        // 否则已删除节点会进入tree_page，后续batchGet装载实体会因orm_logicalDeleted抛异常
        boolean hasCond = appendLogicalDeleteCondition(sb, "b", entityModel);
        if (filter != null) {
            if (hasCond)
                sb.and();
            DaoQueryHelper.appendFilter(sb, "b", filter);
            hasCond = true;
        }
        if (levelProp != null && rootLevelValue != null) {
            if (hasCond)
                sb.and();
            sb.append("\n ").owner("b").append(levelProp).append("=").param(ConvertHelper.toInt(rootLevelValue));
            hasCond = true;
        } else {
            // 如果 parentProp 属性在 filter 中存在，则以传入的属性为准
            boolean hasParentProp = filter != null && ((TreeBean) filter).nodeWithAttr("name", parentProp) != null;
            if (!hasParentProp) {
                if (hasCond)
                    sb.and();
                sb.append("\n ").owner("b").append(parentProp);
                if (StringHelper.isEmpty(rootParentValue)) {
                    sb.append(" is null");
                } else {
                    sb.append(" = ").param(rootParentValue);
                }
                hasCond = true;
            }
        }
        sb.append("\n union all\n");
        appendTreeSql(sb, "o", entityName, pkProp, dispProp, parentProp, levelProp, sortProp, rightJoinProp);
        sb.append(" inner join tree_page p on o.").append(leftJoinProp).append(" = p.joinId ");
        // 递归段同样过滤已删除节点，避免子树遍历穿过逻辑删除记录
        if (isUseLogicalDelete(entityModel)) {
            sb.where();
            appendDeleteFlagEquals(sb, "o", entityModel);
        }
//        if (filter != null) {
//            sb.where();
//            DaoQueryHelper.appendFilter(sb, "o", filter);
//        }
        sb.append(')');
    }

    /**
     * 追加逻辑删除过滤条件。返回是否追加了条件，供调用方决定后续条件是否需要 and 连接。
     */
    static boolean appendLogicalDeleteCondition(SQL.SqlBuilder sb, String owner, IEntityModel entityModel) {
        if (!isUseLogicalDelete(entityModel))
            return false;
        appendDeleteFlagEquals(sb, owner, entityModel);
        return true;
    }

    private static void appendDeleteFlagEquals(SQL.SqlBuilder sb, String owner, IEntityModel entityModel) {
        sb.append("\n ").owner(owner).append(entityModel.getDeleteFlagProp()).append(" = ")
                .param(notDeletedValue(entityModel));
    }

    private static boolean isUseLogicalDelete(IEntityModel entityModel) {
        // entityModel可能为null（实体模型未注册时），此时保持原有行为不过滤
        return entityModel != null && entityModel.isUseLogicalDelete()
                && !StringHelper.isEmpty(entityModel.getDeleteFlagProp());
    }

    /**
     * 与GenSqlHelper.getBooleanLiteral的取值语义保持一致：BOOLEAN列绑定false，VARCHAR列绑定"0"，其余绑定0
     */
    static Object notDeletedValue(IEntityModel entityModel) {
        IColumnModel col = entityModel.getColumnByPropId(entityModel.getDeleteFlagPropId(), false);
        if (col != null) {
            if (col.getStdSqlType() == StdSqlType.BOOLEAN)
                return Boolean.FALSE;
            if (col.getStdSqlType() == StdSqlType.VARCHAR)
                return "0";
        }
        return 0;
    }

    static void appendTreeSql(SQL.SqlBuilder sb, String owner, String objName, String pkProp,
                              String dispProp, String parentProp, String levelProp, String sortProp, String leftJoinProp) {
        sb.sql("select ").owner(owner).append(pkProp).sql(" as id,").owner(owner).append(dispProp).append(" as displayName");
        sb.append(",").owner(owner).append(parentProp).append(" as parentId");
        if (levelProp == null) {
            sb.append(",null as level");
        } else {
            sb.append(",").owner(owner).append(levelProp).append(" as level");
        }
        sb.append(',').owner(owner).append(leftJoinProp).append(" as joinId");
        if (sortProp != null) {
            sb.append(", ").owner(owner).append(sortProp).as(" as sortProp");
        }
        sb.append("\nfrom ").append(objName).append(" ").append(owner);
    }
}
