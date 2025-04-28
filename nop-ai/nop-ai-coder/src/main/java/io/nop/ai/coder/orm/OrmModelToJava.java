package io.nop.ai.coder.orm;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.commons.util.StringHelper;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.model.OrmModelConstants;

/**
 * 将OrmModel转换为Java语言形式，精简表达形式
 */
public class OrmModelToJava {
    private final StringBuilder sb;

    public OrmModelToJava() {
        this(new StringBuilder());
    }

    public OrmModelToJava(StringBuilder sb) {
        this.sb = sb;
    }

    public OrmModelToJava appendOrmModel(IOrmModel ormModel) {
        for (IEntityModel entityModel : ormModel.getEntityModels()) {
            appendEntityModel(entityModel, ormModel);
        }
        return this;
    }

    public String toString() {
        return sb.toString();
    }

    public OrmModelToJava appendEntityModel(IEntityModel entityModel, IOrmModel ormModel) {
        String entityName = entityModel.getShortName();
        String comment = entityModel.getComment();
        if (comment != null) {
            sb.append("/** ").append(comment).append(" */\n");
        }
        sb.append("class ").append(entityName).append("{\n");
        for (IColumnModel colModel : entityModel.getColumns()) {
            sb.append(StringHelper.simplifyJavaType(colModel.getJavaTypeName()));
            sb.append(" ").append(colModel.getName()).append(";");
            if (colModel.getDisplayName() != null) {
                sb.append(" //").append(StringHelper.replace(colModel.getDisplayName(), "\n", " "));
                appendDict(colModel, ormModel);
            }
            sb.append("\n");
        }

        sb.append("\n");

        for (IEntityRelationModel relModel : entityModel.getToOneRelations()) {
            sb.append("@JoinToOne(leftProp=\"").append(relModel.getJoinLeftProps());
            sb.append("\",rightProp=\"").append(relModel.getJoinRightProps());
            sb.append("\")\n");
            sb.append(StringHelper.simpleClassName(relModel.getRefEntityName()));
            sb.append(" ").append(relModel.getName()).append(";");
            if (relModel.getDisplayName() != null) {
                sb.append(" //").append(StringHelper.replace(relModel.getDisplayName(), "\n", " "));
            }
            sb.append("\n\n");
        }

        for (IEntityRelationModel relModel : entityModel.getToManyRelations()) {
            sb.append("@JoinToMany(leftProp=\"").append(relModel.getJoinLeftProps());
            sb.append("\",rightProp=\"").append(relModel.getJoinRightProps());
            sb.append("\")\n");
            sb.append("Set<");
            sb.append(StringHelper.simpleClassName(relModel.getRefEntityName()));
            sb.append("> ").append(relModel.getName()).append(";");
            if (relModel.getDisplayName() != null) {
                sb.append(" //").append(StringHelper.replace(relModel.getDisplayName(), "\n", " "));
            }
            sb.append("\n\n");
        }
        sb.append("}\n\n");
        return this;
    }

    private void appendDict(IColumnModel colModel, IOrmModel ormModel) {
        if (ormModel == null)
            return;

        String dictName = (String) colModel.prop_get(OrmModelConstants.EXT_DICT);
        if (dictName != null) {
            DictBean dict = ormModel.getDict(dictName);
            if (dict != null && dict.getOptions() != null) {
                sb.append(" Options: ");
                for (DictOptionBean option : dict.getOptions()) {
                    sb.append(option.getValue()).append("[");
                    sb.append(option.getLabel()).append("],");
                }
            }
        }
    }
}