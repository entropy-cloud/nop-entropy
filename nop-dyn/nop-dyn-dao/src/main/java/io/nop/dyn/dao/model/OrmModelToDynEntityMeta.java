/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.dao.model;

import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.TagsHelper;
import io.nop.dyn.dao.NopDynDaoConstants;
import io.nop.dyn.dao.entity.NopDynEntityMeta;
import io.nop.dyn.dao.entity.NopDynEntityRelationMeta;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.dyn.dao.entity.NopDynPropMeta;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.model.OrmModelConstants;
import io.nop.orm.model.OrmRelationType;
import io.nop.orm.support.OrmMappingTableMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class OrmModelToDynEntityMeta {
    static final Logger LOG = LoggerFactory.getLogger(OrmModelToDynEntityMeta.class);

    private final boolean removeNotExisting;
    private NopDynModule dynModule;
    private Map<String, NopDynEntityMeta> entityMetas;
    private String basePackageName;
    private String entityPackageName;
    private String entityPackagePrefix;

    public OrmModelToDynEntityMeta(boolean removeNotExisting) {
        this.removeNotExisting = removeNotExisting;
    }

    public void transformModule(IOrmModel ormModel, NopDynModule dynModule) {
        this.basePackageName = (String) ormModel.prop_get(OrmModelConstants.EXT_BASE_PACKAGE_NAME);
        this.entityPackageName = (String) ormModel.prop_get(OrmModelConstants.EXT_ENTITY_PACKAGE_NAME);
        if (entityPackageName == null) {
            entityPackageName = NopDynDaoConstants.DEFAULT_ENTITY_PACKAGE_NAME;
        }
        this.entityPackagePrefix = this.entityPackageName + ".";

        dynModule.setBasePackageName(basePackageName);
        dynModule.setEntityPackageName(entityPackageName);
        dynModule.setMavenGroupId((String) ormModel.prop_get(OrmModelConstants.EXT_MAVEN_GROUP_ID));

        if (removeNotExisting)
            removeNotExistingMetas(ormModel, dynModule);

        this.dynModule = dynModule;
        this.entityMetas = new HashMap<>();
        dynModule.getEntityMetas().forEach(entityMeta -> {
            entityMetas.put(entityMeta.getEntityName(), entityMeta);
        });

        List<OrmMappingTableMeta> mappingTables = new ArrayList<>();
        ormModel.getEntityModels().forEach(entityModel -> {
            // many-to-many的中间表不转换为实体表
            if (OrmMappingTableMeta.isMappingTable(entityModel)) {
                mappingTables.add(new OrmMappingTableMeta(entityModel));
                return;
            }

            NopDynEntityMeta entityMeta = makeEntityMeta(entityModel.getName());
            transformEntityMeta(entityModel, entityMeta);
        });

        for (OrmMappingTableMeta mappingMeta : mappingTables) {
            if (mappingMeta.getRefProp2() == null) {
                LOG.warn("nop.dyn.ignore-invalid-many-to-many-mapping-table:{}", mappingMeta.getMappingTable());
                continue;
            }
            NopDynEntityMeta entityMeta1 = getEntityMeta(entityMetas, mappingMeta.getRefEntityName1());
            if (entityMeta1 == null)
                continue;

            NopDynEntityMeta entityMeta2 = getEntityMeta(entityMetas, mappingMeta.getRefEntityName2());
            if (entityMeta2 == null)
                continue;
            NopDynEntityRelationMeta relMeta1 = new NopDynEntityRelationMeta();
            relMeta1.setEntityMeta(entityMeta1);
            relMeta1.setRefEntityMeta(entityMeta2);
            relMeta1.setMiddleEntityName(mappingMeta.getMappingEntityName());
            //relMeta.setMiddleTableName(mappingMeta.getMappingTableName());
            relMeta1.setTagsText(TagsHelper.toString(mappingMeta.getMappingTable().getTagSet()));
            relMeta1.setRemark(mappingMeta.getMappingTable().getComment());
            relMeta1.setRelationType(OrmRelationType.m2m.name());
            relMeta1.setRelationName(mappingMeta.getMappingPropName1());
            relMeta1.setLeftPropName(OrmModelConstants.PROP_ID);
            relMeta1.setRightPropName(OrmModelConstants.PROP_ID);
            relMeta1.setStatus(0);
            addRelationMeta(entityMeta1, relMeta1);


            NopDynEntityRelationMeta relMeta2 = new NopDynEntityRelationMeta();
            relMeta2.setEntityMeta(entityMeta2);
            relMeta2.setRefEntityMeta(entityMeta1);
            relMeta2.setMiddleEntityName(mappingMeta.getMappingEntityName());
            //relMeta.setMiddleTableName(mappingMeta.getMappingTableName());
            relMeta2.setTagsText(TagsHelper.toString(mappingMeta.getMappingTable().getTagSet()));
            relMeta2.setRemark(mappingMeta.getMappingTable().getComment());
            relMeta2.setRelationType(OrmRelationType.m2m.name());
            relMeta2.setRelationName(mappingMeta.getMappingPropName2());
            relMeta2.setLeftPropName(OrmModelConstants.PROP_ID);
            relMeta2.setRightPropName(OrmModelConstants.PROP_ID);
            relMeta2.setStatus(1);
            addRelationMeta(entityMeta2, relMeta2);
        }
    }

    private NopDynEntityMeta makeEntityMeta(String entityName) {
        // ORM模型按全名（entityPackageName前缀+简名）索引实体，而entityMetas以简名为key。
        // 必须先统一口径再查找，否则对存量模块重导入时同名实体会被新建一份（主键漂移、级联元数据全部重建）
        String simpleEntityName = StringHelper.removeHead(entityName, this.entityPackagePrefix);
        NopDynEntityMeta entityMeta = entityMetas.get(simpleEntityName);
        if (entityMeta == null) {
            entityMeta = new NopDynEntityMeta();
            entityMeta.setEntityName(simpleEntityName);
            entityMeta.setDisplayName(StringHelper.simpleClassName(entityMeta.getEntityName()));
            entityMeta.setStatus(1);
            entityMeta.setStoreType(NopDynDaoConstants.ENTITY_STORE_TYPE_VIRTUAL);
            entityMeta.setIsExternal(false);
            dynModule.getEntityMetas().add(entityMeta);
            // 同步写入查找表，同轮内再次引用该实体（如关联的ref实体）时才能复用，否则会新建重复meta
            entityMetas.put(simpleEntityName, entityMeta);
        }
        return entityMeta;
    }

    private NopDynEntityMeta getEntityMeta(Map<String, NopDynEntityMeta> entityMetas, String entityName) {
        NopDynEntityMeta entityMeta = entityMetas.get(entityName);
        if (entityMeta == null) {
            entityMeta = entityMetas.get(StringHelper.simpleClassName(entityName));
        }
        return entityMeta;
    }

    private void removeNotExistingMetas(IOrmModel ormModel, NopDynModule dynModule) {
        // 删除被删除的模块
        Iterator<NopDynEntityMeta> it = dynModule.getEntityMetas().iterator();
        while (it.hasNext()) {
            NopDynEntityMeta entityMeta = it.next();

            if (!entityExists(ormModel, entityMeta.getEntityName())) {
                it.remove();
            }
        }
    }

    /**
     * ormModel以实体全名为key（仅registerShortName的实体才额外注册简名），而存量meta存的是简名，
     * 判断存在性时必须补全entityPackagePrefix后再查，否则会把仍存在的实体全部误删后重建
     */
    private boolean entityExists(IOrmModel ormModel, String simpleEntityName) {
        if (ormModel.getEntityModel(simpleEntityName) != null)
            return true;
        return ormModel.getEntityModel(this.entityPackagePrefix + simpleEntityName) != null;
    }

    private void transformEntityMeta(IEntityModel entityModel, NopDynEntityMeta entityMeta) {
        entityMeta.setTableName(entityModel.getTableName());
        entityMeta.setTagsText(StringHelper.join(entityModel.getTagSet(), ","));
        entityMeta.setRemark(entityModel.getComment());
        if (entityModel.getDisplayName() != null)
            entityMeta.setDisplayName(entityModel.getDisplayName());
        entityMeta.setIsExternal(entityModel.containsTag(OrmModelConstants.TAG_NOT_GEN));

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

        // 只考虑单字段关联
        entityModel.getToOneRelations().forEach(rel -> {
            if (rel.getJoin().size() == 1) {
                IEntityJoinConditionModel join = rel.getJoin().get(0);
                if (join.getLeftProp() != null) {
                    NopDynPropMeta propMeta = propMetas.get(join.getLeftProp());
                    if (propMeta != null) {
                        NopDynEntityRelationMeta relMeta1 = new NopDynEntityRelationMeta();
                        relMeta1.setRelationDisplayName(rel.getDisplayName());
                        NopDynEntityMeta entityMeta1 = makeEntityMeta(rel.getOwnerEntityModel().getName());
                        NopDynEntityMeta entityMeta2 = makeEntityMeta(rel.getRefEntityName());
                        relMeta1.setEntityMeta(entityMeta1);
                        relMeta1.setRefEntityMeta(entityMeta2);
                        relMeta1.setTagsText(TagsHelper.toString(rel.getTagSet()));
                        relMeta1.setRelationName(rel.getName());
                        relMeta1.setRelationType(rel.isOneToOne() ? OrmRelationType.o2o.name() : OrmRelationType.m2o.name());
                        relMeta1.setLeftPropName(join.getLeftProp());
                        relMeta1.setRightPropName(join.getRightProp());
                        relMeta1.setStatus(1);
                        addRelationMeta(entityMeta1, relMeta1);

                        if (rel.getRefPropName() != null) {
                            NopDynEntityRelationMeta relMeta2 = new NopDynEntityRelationMeta();
                            relMeta2.setRelationDisplayName(rel.getDisplayName());
                            relMeta2.setEntityMeta(entityMeta2);
                            relMeta2.setRefEntityMeta(entityMeta1);
                            relMeta2.setTagsText(TagsHelper.toString(rel.getTagSet()));
                            relMeta2.setRelationName(rel.getRefPropName());
                            relMeta2.setRelationType(rel.isOneToOne() ? OrmRelationType.o2o.name() : OrmRelationType.o2m.name());
                            relMeta2.setLeftPropName(join.getRightProp());
                            relMeta2.setRightPropName(join.getLeftProp());
                            relMeta2.setStatus(1);
                            addRelationMeta(entityMeta2, relMeta2);
                        }
                    }
                }
            }
        });
    }

    NopDynPropMeta makeProp(NopDynEntityMeta entityMeta, Map<String, NopDynPropMeta> propMetas, String propName) {
        NopDynPropMeta propMeta = propMetas.get(propName);
        if (propMeta == null) {
            propMeta = new NopDynPropMeta();
            propMeta.setPropName(propName);
            entityMeta.getPropMetas().add(propMeta);
            // 同步写入查找表，后续to-one关联按join.leftProp查列时才能命中（否则新建列上的关联被静默丢弃）
            propMetas.put(propName, propMeta);
        }
        return propMeta;
    }

    /**
     * 对存量模块重复转换时按relationName去重（替换旧meta），避免关联元数据在同轮或跨轮叠加翻倍
     */
    private static void addRelationMeta(NopDynEntityMeta entityMeta, NopDynEntityRelationMeta relMeta) {
        entityMeta.getRelationMetasForEntity().removeIf(
                existing -> Objects.equals(existing.getRelationName(), relMeta.getRelationName()));
        entityMeta.getRelationMetasForEntity().add(relMeta);
    }

    void transformPropMeta(IEntityPropModel prop, NopDynPropMeta propMeta) {
        if (prop.isColumnModel()) {
            transformColumnMeta((IColumnModel) prop, propMeta);
        }
    }

    void transformColumnMeta(IColumnModel col, NopDynPropMeta propMeta) {
        propMeta.setPropName(col.getName());
        propMeta.setPropId(col.getPropId());
        StdSqlType sqlType = col.getStdSqlType();
        if (sqlType == null)
            sqlType = StdSqlType.VARCHAR;
        propMeta.setStdSqlType(sqlType.getName());
        propMeta.setPrecision(col.getPrecision());
        propMeta.setScale(col.getScale());
        propMeta.setDisplayName(col.getDisplayName());
        propMeta.setTagSet(col.getTagSet());
        propMeta.setStdDomainName(col.getStdDomain());
        propMeta.setIsMandatory(col.isMandatory());
        propMeta.setRemark(col.getComment());
        propMeta.setUiControl((String) col.prop_get(OrmModelConstants.EXT_UI_CONTROL));
        propMeta.setUiShow((String) col.prop_get(OrmModelConstants.EXT_UI_SHOW));
        propMeta.setStatus(1);

        // 只有AI生成的模型会使用这个属性，假定实体名已经正确设置
        String refTable = (String) col.prop_get(NopDynDaoConstants.EXT_ORM_REF_TABLE);
        if (refTable != null) {
            addRefTable(col, refTable);
        }
    }

    private void addRefTable(IColumnModel col, String refTable) {
        String refEntityName = StringHelper.camelCase(refTable, true);

        NopDynEntityRelationMeta relMeta1 = new NopDynEntityRelationMeta();
        relMeta1.setStatus(1);
        relMeta1.setRelationName(getRefPropNameFromColCode(col.getCode(), refEntityName));
        relMeta1.setRelationDisplayName(col.getDisplayName());
        NopDynEntityMeta entityMeta1 = makeEntityMeta(col.getOwnerEntityModel().getName());
        NopDynEntityMeta entityMeta2 = makeEntityMeta(refEntityName);
        relMeta1.setEntityMeta(entityMeta1);
        relMeta1.setRefEntityMeta(entityMeta2);
        relMeta1.setRelationType(col.isPrimary() ? OrmRelationType.o2o.name() : OrmRelationType.m2o.name());
        relMeta1.setLeftPropName(col.getName());
        relMeta1.setRightPropName(OrmModelConstants.PROP_ID);
        addRelationMeta(entityMeta1, relMeta1);
    }

    String getRefPropNameFromColCode(String colCode, String refEntityName) {
        if (colCode.equalsIgnoreCase("_id"))
            return refEntityName;

        // 必须先判_id后缀再判id结尾，否则小写user_id会被endsWith("id")短路，与大写USER_ID的命名行为分叉
        if (StringHelper.endsWithIgnoreCase(colCode, "_id")) {
            return StringHelper.camelCase(colCode.substring(0, colCode.length() - "_id".length()), false);
        }
        if (colCode.endsWith("id"))
            return refEntityName;
        return StringHelper.camelCase(colCode, false) + "Obj";
    }
}