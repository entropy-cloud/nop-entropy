package io.nop.orm.model.schema;

import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.OrmModelConstants;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.IObjSchema;
import io.nop.xlang.xmeta.ISchema;

import java.util.List;
import java.util.stream.Collectors;

public class SchemaToOrmModel {
    private IEntityModel ormDefaults;

    private IEntityModel getOrmDefaults() {
        if (ormDefaults == null) {
            IOrmModel ormModel = (IOrmModel) ResourceComponentManager.instance().loadComponentModel(OrmModelConstants.DSL_DEFAULT_ORM_MODEL_PATH);
            ormDefaults = ormModel.requireEntityModel(OrmModelConstants.ENTITY_NAME_NOP_DEFAULT_COLS);
        }
        return ormDefaults;
    }

    public OrmModel transform(List<? extends ISchema> schemas) {
        OrmModel ormModel = new OrmModel();
        for (ISchema schema : schemas) {
            if (!schema.isObjSchema())
                continue;
            ormModel.addEntity(toEntityModel(schema));
        }
        return ormModel;
    }

    private OrmEntityModel toEntityModel(IObjSchema schema) {
        OrmEntityModel entityModel = new OrmEntityModel();
        entityModel.setTableName(StringHelper.camelCaseToUnderscore(schema.getName(), true));
        entityModel.setName(schema.getName());
        entityModel.setDisplayName(schema.getDisplayName());
        IColumnModel sidCol = getOrmDefaults().getColumn(OrmModelConstants.PROP_NAME_sid, false);
        entityModel.addColumn(copy(sidCol));
        for (IObjPropMeta propMeta : schema.getProps()) {
            if (propMeta.getSchema() != null && !propMeta.getSchema().isSimpleSchema())
                continue;
            // 忽略扩展属性
            if (propMeta.getName().indexOf(':') > 0)
                continue;
            entityModel.addColumn(toColumnModel(propMeta));
        }
        addDefaultCols(entityModel);
        resetPropId(entityModel);
        return entityModel;
    }

    private OrmColumnModel copy(IColumnModel col) {
        return ((OrmColumnModel) col).cloneInstance();
    }

    private void resetPropId(OrmEntityModel entityModel) {
        for (int i = 0, n = entityModel.getColumns().size(); i < n; i++) {
            entityModel.getColumns().get(i).setPropId(i + 1);
        }
    }

    private void addDefaultCols(OrmEntityModel entityModel) {
        for (IColumnModel col : getOrmDefaults().getColumns()) {
            if (col.isPrimary())
                continue;
            if (entityModel.getColumn(col.getName()) != null)
                continue;
            entityModel.addColumn(copy(col));
        }
    }

    private OrmColumnModel toColumnModel(IObjPropMeta propMeta) {
        OrmColumnModel col = new OrmColumnModel();
        col.setName(propMeta.getName());
        col.setCode(normalizeColCode(propMeta));
        col.setDisplayName(getDisplayName(propMeta));
        String enName = buildEnNameFromName(propMeta.getName());
        col.prop_set(OrmModelConstants.EXT_I18N_EN_DISPLAY_NAME, enName);
        StdDataType dataType = propMeta.getStdDataType();
        if (dataType == null)
            dataType = StdDataType.STRING;

        if (propMeta.isMandatory()) {
            col.setMandatory(true);
        }

        StdSqlType sqlType = StdSqlType.fromStdDataTYpe(dataType);
        if (sqlType == null)
            sqlType = StdSqlType.VARCHAR;

        col.setStdSqlType(sqlType);
        if (sqlType == StdSqlType.DECIMAL) {
            col.setPrecision(20);
            col.setScale(6);
        } else if (sqlType == StdSqlType.VARCHAR) {
            col.setPrecision(50);
        } else if (sqlType == StdSqlType.ANY) {
            col.setStdSqlType(StdSqlType.VARCHAR);
            col.setPrecision(100);
        } else if (sqlType == StdSqlType.ARRAY) {
            col.setStdSqlType(StdSqlType.VARCHAR);
            col.setPrecision(200);
        }
        col.setDefaultValue(StringHelper.toString(propMeta.getDefaultValue(), null));
        return col;
    }

    String normalizeColCode(IObjPropMeta propMeta) {
        String code = StringHelper.camelCaseToUnderscore(propMeta.getName(), false);
        if ("TAG_SET".equals(code))
            return "TAG_SET_TEXT";
        return code;
    }


    String getDisplayName(IObjPropMeta propMeta) {
        String displayName = propMeta.getDisplayName();
        if (!StringHelper.isEmpty(displayName)) {
            return displayName;
        }
        String name = propMeta.getName();
        if (name.equals("name"))
            return "名称";
        if (name.equals("displayName"))
            return "显示名称";
        if (name.equals("comment")) {
            return "注释";
        }
        if (name.equals("tagSet")) {
            return "标签";
        }
        if(name.equals("type")){
            return "类型";
        }
        return null;
    }

    static String buildEnNameFromName(String name) {
        String code = StringHelper.camelCaseToUnderscore(name, true);
        List<String> list = StringHelper.split(code, '_');
        return list.stream().map(StringHelper::capitalize).collect(Collectors.joining(" "));
    }
}
