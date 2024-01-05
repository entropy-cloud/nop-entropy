package io.nop.dyn.dao.model;

import io.nop.commons.util.StringHelper;
import io.nop.dyn.dao.entity.NopDynEntityMeta;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.dyn.dao.entity.NopDynPropMeta;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.model.OrmModelConstants;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class OrmModelToDynEntityMeta {
    public void transformModule(IOrmModel ormModel, NopDynModule dynModule) {
        dynModule.setBasePackageName((String) ormModel.prop_get(OrmModelConstants.EXT_BASE_PACKAGE_NAME));
        dynModule.setMavenGroupId((String) ormModel.prop_get(OrmModelConstants.EXT_MAVEN_GROUP_ID));

        //removeNotExistingMetas(ormModel, dynModule);

        Map<String, NopDynEntityMeta> entityMetas = new HashMap<>();
        dynModule.getEntityMetas().forEach(entityMeta -> {
            entityMetas.put(entityMeta.getEntityName(), entityMeta);
        });

        ormModel.getEntityModels().forEach(entityModel -> {
            NopDynEntityMeta entityMeta = entityMetas.get(entityModel.getName());
            if (entityMeta == null) {
                entityMeta = new NopDynEntityMeta();
                entityMeta.setEntityName(entityModel.getName());
                dynModule.getEntityMetas().add(entityMeta);
            }
            transformEntityMeta(entityModel, entityMeta);
        });
    }

    private void removeNotExistingMetas(IOrmModel ormModel, NopDynModule dynModule) {
        // 删除被删除的模块
        Iterator<NopDynEntityMeta> it = dynModule.getEntityMetas().iterator();
        while (it.hasNext()) {
            NopDynEntityMeta entityMeta = it.next();

            if (ormModel.getEntityModel(entityMeta.getEntityName()) == null) {
                it.remove();
            }
        }
    }

    private void transformEntityMeta(IEntityModel entityModel, NopDynEntityMeta entityMeta) {
        entityMeta.setTableName(entityModel.getTableName());
        entityMeta.setTagSet(StringHelper.join(entityModel.getTagSet(), ","));
        entityMeta.setRemark(entityModel.getComment());
        entityMeta.setStatus(1);
        entityMeta.setDisplayName(entityModel.getDisplayName());

        Map<String, NopDynPropMeta> propMetas = new HashMap<>();
        entityMeta.getPropMetas().forEach(propMeta -> {
            propMetas.put(propMeta.getPropName(), propMeta);
        });

        entityModel.getColumns().forEach(col -> {
            // 忽略主键
            if (col.isPrimary())
                return;

            NopDynPropMeta propMeta = makeProp(entityMeta, propMetas, col.getName());

            transformPropMeta(col, propMeta);
        });
    }

    NopDynPropMeta makeProp(NopDynEntityMeta entityMeta, Map<String, NopDynPropMeta> propMetas, String propName) {
        NopDynPropMeta propMeta = propMetas.get(propName);
        if (propMeta == null) {
            propMeta = new NopDynPropMeta();
            propMeta.setPropName(propName);
            entityMeta.getPropMetas().add(propMeta);
        }
        return propMeta;
    }

    void transformPropMeta(IEntityPropModel prop, NopDynPropMeta propMeta) {
        if (prop.isColumnModel()) {
            transformColumnMeta((IColumnModel) prop, propMeta);
        }
    }

    void transformColumnMeta(IColumnModel col, NopDynPropMeta propMeta) {
        propMeta.setPropName(col.getName());
        propMeta.setStdSqlType(col.getStdSqlType().getName());
        propMeta.setPrecision(col.getPrecision());
        propMeta.setScale(col.getScale());
        propMeta.setDisplayName(col.getDisplayName());
        propMeta.setTagSet(StringHelper.join(col.getTagSet(), ","));
        propMeta.setStdDomainName(col.getStdDomain());
        propMeta.setIsMandatory(col.isMandatory());
        propMeta.setRemark(col.getComment());
        propMeta.setUiShow((String) col.prop_get(OrmModelConstants.EXT_UI_CONTROL));
        propMeta.setUiControl((String) col.prop_get(OrmModelConstants.EXT_UI_SHOW));
    }
}
