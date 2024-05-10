package io.nop.biz.crud;

import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.BizConstants;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.dao.DaoQueryHelper;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.impl.ObjTreeModel;

import static io.nop.biz.BizErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.biz.BizErrors.ERR_BIZ_NOT_SUPPORT_TREE_MODEL;
import static io.nop.biz.BizErrors.ERR_BIZ_TREE_ENTITY_NO_PARENT_PROP;

public class TreeEntityHelper {
    public static SQL.SqlBuilder buildTreeEntityCountSql(IObjMeta objMeta, ITreeBean filter) {
        SQL.SqlBuilder sb = SQL.begin();
        buildTreeEntityBaseSql(sb, objMeta, filter);
        sb.sql("\n select count(1) from tree_page t");
        return sb;
    }

    public static SQL.SqlBuilder buildTreeEntitySql(IObjMeta objMeta, ITreeBean filter) {
        SQL.SqlBuilder sb = SQL.begin();
        buildTreeEntityBaseSql(sb, objMeta, filter);
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

    private static void buildTreeEntityBaseSql(SQL.SqlBuilder sb, IObjMeta objMeta, ITreeBean filter) {
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
        if (filter != null) {
            DaoQueryHelper.appendFilter(sb, "b", filter);
        }
        if (levelProp != null && rootLevelValue != null) {
            sb.append("\n ").owner("b").append(levelProp).append("=").param(ConvertHelper.toInt(rootLevelValue));
        } else {
            // 如果 parentProp 属性在 filter 中存在，以传入的属性为准
            boolean hasParentProp = filter != null && ((TreeBean) filter).childWithAttr("name", parentProp) == null;
            if (hasParentProp) {
                if (filter != null)
                    sb.and();
                sb.append("\n ").owner("b").append(parentProp);
                if (StringHelper.isEmpty(rootParentValue)) {
                    sb.append(" is null");
                } else {
                    sb.append(" = ").param(rootParentValue);
                }
            }
        }
        sb.append("\n union all\n");
        appendTreeSql(sb, "o", entityName, pkProp, dispProp, parentProp, levelProp, sortProp, rightJoinProp);
        sb.append(" inner join tree_page p on o.").append(leftJoinProp).append(" = p.joinId ");
//        if (filter != null) {
//            sb.where();
//            DaoQueryHelper.appendFilter(sb, "o", filter);
//        }
        sb.append(')');
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
