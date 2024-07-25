/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.dao.model;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.TagsHelper;
import io.nop.core.reflect.ReflectionManager;
import io.nop.dao.api.DaoProvider;
import io.nop.dyn.dao.NopDynDaoConstants;
import io.nop.dyn.dao.entity.NopDynDomain;
import io.nop.dyn.dao.entity.NopDynEntity;
import io.nop.dyn.dao.entity.NopDynEntityMeta;
import io.nop.dyn.dao.entity.NopDynEntityRelation;
import io.nop.dyn.dao.entity.NopDynEntityRelationMeta;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.dyn.dao.entity.NopDynPropMeta;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.OrmAliasModel;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmDomainModel;
import io.nop.orm.model.OrmEntityFilterModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmJoinOnModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.OrmModelConstants;
import io.nop.orm.model.OrmReferenceModel;
import io.nop.orm.model.OrmRelationType;
import io.nop.orm.model.OrmToManyReferenceModel;
import io.nop.orm.model.OrmToOneReferenceModel;
import io.nop.orm.support.DynamicOrmEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static io.nop.dyn.dao.NopDynDaoErrors.ARG_ENTITY_NAME;
import static io.nop.dyn.dao.NopDynDaoErrors.ARG_PROP_MAPPING;
import static io.nop.dyn.dao.NopDynDaoErrors.ARG_PROP_NAME;
import static io.nop.dyn.dao.NopDynDaoErrors.ARG_STD_SQL_TYPE;
import static io.nop.dyn.dao.NopDynDaoErrors.ERR_DYN_UNKNOWN_STD_SQL_TYPE;
import static io.nop.dyn.dao.NopDynDaoErrors.ERR_DYN_VIRTUAL_ENTITY_PROP_MAPPING_NOT_VALID;

public class DynEntityMetaToOrmModel {
    private final IEntityModel dynEntityModel;
    private final IEntityModel dynRelationModel;
    private final boolean forceRealTable;
    private Map<String, OrmEntityModel> middleTables = new HashMap<>();

    static final List<String> STD_PROPS = Arrays.asList(NopDynEntity.PROP_NAME_version,
            NopDynEntity.PROP_NAME_createdBy, NopDynEntity.PROP_NAME_createTime,
            NopDynEntity.PROP_NAME_updatedBy, NopDynEntity.PROP_NAME_updateTime);

    public DynEntityMetaToOrmModel(boolean forceRealTable) {
        this.dynEntityModel = ((IOrmEntityDao<?>) DaoProvider.instance().daoFor(NopDynEntity.class)).getEntityModel();
        this.dynRelationModel = ((IOrmEntityDao<?>) DaoProvider.instance().daoFor(NopDynEntityRelation.class)).getEntityModel();
        this.forceRealTable = forceRealTable;
    }

    public OrmModel transformModule(NopDynModule module) {
        OrmModel model = new OrmModel();
        model.setDomains(toOrmDomains(getDomains(module)));
        if (module.getBasePackageName() != null) {
            model.prop_set(OrmModelConstants.EXT_BASE_PACKAGE_NAME, module.getBasePackageName());
        }
        model.prop_set(OrmModelConstants.EXT_APP_NAME, module.getModuleName());

        if (module.getMavenGroupId() != null) {
            model.prop_set(OrmModelConstants.EXT_MAVEN_GROUP_ID, module.getMavenGroupId());
        }

        model.prop_set(OrmModelConstants.EXT_MAVEN_ARTIFACT_ID, module.getModuleName());

        String basePackageName = module.getBasePackageName();
        if (basePackageName == null)
            basePackageName = NopDynDaoConstants.DEFAULT_BASE_PACKAGE_NAME;

        model.setEntities(toOrmEntityModels(module.getEntityMetas(), basePackageName));

        if (!forceRealTable) {
            addExternalExtTable(model);
        }
        model.init();
        return model;
    }

    void addExternalExtTable(OrmModel model) {
        IEntityModel refModel = dynEntityModel.getRelation(NopDynEntity.PROP_NAME_extFields, false).getRefEntityModel();
        OrmEntityModel external = new OrmEntityModel();
        external.setTableName(refModel.getTableName());
        external.setDisplayName(refModel.getDisplayName());
        external.setNotGenCode(true);
        external.setName(refModel.getName());
        for (IColumnModel col : refModel.getColumns()) {
            forceAddCol(external, col);
        }
        model.addEntity(external);
    }

    List<OrmEntityModel> toOrmEntityModels(Collection<NopDynEntityMeta> entityMetas, String basePackageName) {
        // 如果是外部表，或者没有属性，则不需要生成对应的实体定义
        List<OrmEntityModel> ret = entityMetas.stream().filter(entityMeta -> {
            return entityMeta.isHasProp() || Boolean.TRUE.equals(entityMeta.getIsExternal());
        }).map(this::toOrmEntityModel).collect(Collectors.toList());

        addRelationTables(ret, entityMetas, basePackageName);
        return ret;
    }

    OrmEntityModel toOrmEntityModel(NopDynEntityMeta entityMeta) {
        OrmEntityModel ret = new OrmEntityModel();
        ret.setName(entityMeta.getEntityName());
        ret.setDisplayName(entityMeta.getDisplayName());
        ret.setTableName(entityMeta.forceGetTableName());
        ret.setTagSet(ConvertHelper.toCsvSet(entityMeta.getTagsText()));
        ret.setRegisterShortName(true);
        ret.setUseTenant(dynEntityModel.isUseTenant());
        ret.setComment(entityMeta.getRemark());

        // 强制使用sid作为主键，从而简化用户层面的配置
        OrmColumnModel idCol = forceAddCol(ret, dynEntityModel.getColumn(NopDynEntity.PROP_NAME_sid, false));
        idCol.prop_set(OrmModelConstants.EXT_UI_SHOW, "X");

        if (isVirtualTable(entityMeta)) {
            buildVirtualEntityModel(ret, entityMeta);
            // 动态表的propId使用的是NopDynEntity实体已经定义的propId，不可能重复
            addStdColumns(ret);
        } else {
            buildRealEntityModel(ret, entityMeta);
            addStdColumns(ret);
            normalizePropIds(ret);
        }

        return ret;
    }

    boolean isVirtualTable(NopDynEntityMeta entityMeta) {
        return !forceRealTable && entityMeta.getStoreType() == NopDynDaoConstants.ENTITY_STORE_TYPE_VIRTUAL;
    }

    protected void normalizePropIds(OrmEntityModel entityModel) {
        int nextPropId = 1;
        for (OrmColumnModel col : entityModel.getColumns()) {
            col.setPropId(nextPropId++);
        }
    }

    protected void addStdColumns(OrmEntityModel entityModel) {
        IColumnModel versionProp = dynEntityModel.getColumn(NopDynEntity.PROP_NAME_version, false);
        IColumnModel createdByProp = dynEntityModel.getColumn(NopDynEntity.PROP_NAME_createdBy, false);
        IColumnModel updatedByProp = dynEntityModel.getColumn(NopDynEntity.PROP_NAME_updatedBy, false);
        IColumnModel updateTimeProp = dynEntityModel.getColumn(NopDynEntity.PROP_NAME_updateTime, false);
        IColumnModel createTimeProp = dynEntityModel.getColumn(NopDynEntity.PROP_NAME_createTime, false);

        forceAddCol(entityModel, versionProp);
        entityModel.setVersionProp(versionProp.getName());

        if (dynEntityModel.getTenantColumn() != null) {
            forceAddCol(entityModel, dynEntityModel.getTenantColumn());
            entityModel.setTenantProp(dynEntityModel.getTenantColumn().getName());
        }

        forceAddCol(entityModel, createTimeProp);
        entityModel.setCreateTimeProp(createTimeProp.getName());

        forceAddCol(entityModel, updateTimeProp);
        entityModel.setUpdateTimeProp(updateTimeProp.getName());

        forceAddCol(entityModel, createdByProp);
        entityModel.setCreaterProp(createdByProp.getName());

        forceAddCol(entityModel, updatedByProp);
        entityModel.setUpdaterProp(updatedByProp.getName());
    }

    private OrmColumnModel forceAddCol(OrmEntityModel entityModel, IColumnModel stdCol) {
        OrmColumnModel col = entityModel.getColumn(stdCol.getName());
        if (col == null) {
            col = ((OrmColumnModel) stdCol).cloneInstance();
            col.setDomain(null);
            entityModel.addColumn(col);
        }
        return col;
    }

    protected void buildRealEntityModel(OrmEntityModel entityModel, NopDynEntityMeta entityMeta) {
        entityModel.setClassName(DynamicOrmEntity.class.getName());

        entityMeta.getPropMetas().forEach(propMeta -> {
            entityModel.addColumn(toColumnModel(propMeta));
//            if (propMeta.getRefEntityName() != null) {
//                entityModel.addRelation(toRefModel(propMeta));
//            }
        });

        entityMeta.getRelationMetasForEntity().forEach(rel -> {
            handleRelationMeta(entityModel, rel);
        });
    }

    private void handleRelationMeta(OrmEntityModel entityModel, NopDynEntityRelationMeta rel) {

        OrmRelationType ormRelationType = OrmRelationType.valueOf(rel.getRelationType());
        if (ormRelationType == OrmRelationType.o2o
                || ormRelationType == OrmRelationType.o2m
                || ormRelationType == OrmRelationType.m2o) {

            OrmReferenceModel oneRelationModel = this.toRelationModel(rel);
            entityModel.addRelation(oneRelationModel);

        } else if (ormRelationType == OrmRelationType.m2m) {
            handleManyToManyRelation(entityModel, rel);
        }
    }

    private void handleManyToManyRelation(OrmEntityModel entityModel, NopDynEntityRelationMeta rel) {
        // 多对多关联的定义是： 新建一个中间表，分别引用左表和右表的主键。这要求rel的leftProp和rightProp都应该是id
        OrmReferenceModel manyRelationModel = this.toRelationModel(rel);
        entityModel.addRelation(manyRelationModel);
        entityModel.setTagSet(TagsHelper.add(StringHelper.parseCsvSet(rel.getTagsText()), OrmModelConstants.TAG_MANY_TO_MANY));
    }


    private OrmReferenceModel toRelationModel(NopDynEntityRelationMeta rel) {
        OrmReferenceModel ret;
        if (rel.isToMany()) {
            ret = new OrmToManyReferenceModel();
        } else {
            ret = new OrmToOneReferenceModel();
        }

        ret.setName(rel.getRelationName());
        ret.setDisplayName(rel.getRelationDisplayName());
        ret.setRefEntityName(rel.getRefEntityMeta().getEntityName());
        ret.setTagSet(StringHelper.parseCsvSet(rel.getTagsText()));

        List<OrmJoinOnModel> join = new ArrayList<>(1);
        OrmJoinOnModel joinOn = new OrmJoinOnModel();
        joinOn.setLeftProp(rel.getLeftPropName());
        joinOn.setRightProp(rel.getRightPropName());
        join.add(joinOn);
        ret.setJoin(join);
        return ret;
    }

    protected void buildVirtualEntityModel(OrmEntityModel entityModel, NopDynEntityMeta entityMeta) {
        entityModel.setTableView(true);
        entityModel.setTableName(dynEntityModel.getTableName());

        entityModel.setClassName(NopDynEntity.class.getName());
        List<OrmEntityFilterModel> filters = new ArrayList<>();
        filters.add(OrmEntityFilterModel.of(NopDynEntity.PROP_NAME_nopObjType, entityMeta.getBizObjName()));
        entityModel.setFilters(filters);

        OrmColumnModel objTypeCol = forceAddCol(entityModel,
                dynEntityModel.getColumn(NopDynEntity.PROP_NAME_nopObjType, false));
        objTypeCol.setTagSet(TagsHelper.add(objTypeCol.getTagSet(), OrmModelConstants.TAG_NOT_PUB));

        entityMeta.getPropMetas().forEach(propMeta -> {
            if (propMeta.getDynPropMapping() != null && !propMeta.getPropName().equals(propMeta.getDynPropMapping())) {
                IColumnModel col = dynEntityModel.getColumn(propMeta.getDynPropMapping(), true);
                if (col == null)
                    throw new NopException(ERR_DYN_VIRTUAL_ENTITY_PROP_MAPPING_NOT_VALID)
                            .param(ARG_ENTITY_NAME, entityMeta.getEntityName())
                            .param(ARG_PROP_NAME, propMeta.getPropName())
                            .param(ARG_PROP_MAPPING, propMeta.getDynPropMapping());
                OrmColumnModel baseCol = ((OrmColumnModel) col).cloneInstance();
                entityModel.addColumn(baseCol);
                entityModel.addAlias(toAliasModel(propMeta));
            } else {
                if (STD_PROPS.contains(propMeta.getPropName()))
                    return;

                OrmAliasModel propModel = toAliasModel(propMeta);
                propModel.setTagSet(TagsHelper.add(propModel.getTagSet(), OrmModelConstants.TAG_EAGER));
                propModel.setPropPath(buildVirtualPropPath(propModel));
                entityModel.addAlias(propModel);
//                if (propMeta.getRefEntityName() != null) {
//                    entityModel.addRelation(toRefModel(propMeta));
//                }
            }

//            if (!StringHelper.isEmpty(propMeta.getRefEntityName())) {
//                addRelation(entityModel, propMeta);
//            }
        });

        addExtFields(entityModel);
    }

    protected void addExtFields(OrmEntityModel entityModel) {
        IEntityRelationModel rel = dynEntityModel.getRelation(NopDynEntity.PROP_NAME_extFields, false);
        OrmToManyReferenceModel ref = ((OrmToManyReferenceModel) rel).cloneInstance();
        entityModel.addRelation(ref);
    }

    protected String buildVirtualPropPath(OrmAliasModel alias) {
        StdDataType type = alias.getType().getStdDataType();
        return "extFields." + alias.getName() + "." + type;
    }

    protected OrmAliasModel toAliasModel(NopDynPropMeta propMeta) {
        OrmAliasModel ret = new OrmAliasModel();
        ret.setName(propMeta.getPropName());
        ret.setDisplayName(propMeta.getDisplayName());
        ret.setTagSet(TagsHelper.add(propMeta.getTagSet(), OrmModelConstants.TAG_EAGER));

        StdSqlType sqlType = toStdSqlType(propMeta.getStdSqlType());
        ret.setType(ReflectionManager.instance().buildRawType(sqlType.getStdDataType().getJavaClass()));
        ret.setPropPath(propMeta.getDynPropMapping());
        return ret;
    }

    protected OrmColumnModel toColumnModel(NopDynPropMeta propMeta) {
        OrmColumnModel ret = new OrmColumnModel();
        ret.setName(propMeta.getPropName());
        ret.setCode(StringHelper.camelCaseToUnderscore(propMeta.getPropName(), false));
        Set<String> tagSet = propMeta.getTagSet();
        ret.setTagSet(tagSet);
        ret.setComment(propMeta.getRemark());

        ret.setMandatory(Boolean.TRUE.equals(propMeta.getIsMandatory()));
        ret.setPrimary(false);

        ret.setDefaultValue(propMeta.getDefaultValue());
        ret.setStdDomain(propMeta.getStdDomainName());
        ret.setPropId(propMeta.getPropId());

        StdSqlType sqlType = toStdSqlType(propMeta.getStdSqlType());
        ret.setStdSqlType(sqlType);

        if (sqlType.isAllowPrecision()) {
            if (propMeta.getPrecision() != null) {
                ret.setPrecision(propMeta.getPrecision());
            } else {
                ret.setPrecision(1);
            }
        }

        if (sqlType.isAllowScale()) {
            if (propMeta.getScale() != null) {
                ret.setScale(propMeta.getScale());
            } else {
                ret.setScale(0);
            }
        }

        NopDynDomain domain = propMeta.getDomain();
        if (domain != null) {
            sqlType = toStdSqlType(propMeta.getStdSqlType());
            ret.setStdSqlType(sqlType);

            if (sqlType.isAllowPrecision()) {
                if (propMeta.getPrecision() != null) {
                    ret.setPrecision(propMeta.getPrecision());
                } else {
                    ret.setPrecision(1);
                }
            }

            if (sqlType.isAllowScale()) {
                if (propMeta.getScale() != null) {
                    ret.setScale(propMeta.getScale());
                } else {
                    ret.setScale(0);
                }
            }

            if (domain.getStdDomainName() != null) ret.setStdDomain(domain.getStdDomainName());
        }

        return ret;
    }

    List<OrmDomainModel> toOrmDomains(List<NopDynDomain> domains) {
        return domains.stream().map(this::toOrmDomain).collect(Collectors.toList());
    }

    List<NopDynDomain> getDomains(NopDynModule module) {
        return module.getEntityMetas().stream().flatMap(entityMeta -> entityMeta.getPropMetas().stream())
                .map(NopDynPropMeta::getDomain)
                .filter(Objects::nonNull)
                .distinct().sorted(NopDynDomain::compareTo).collect(Collectors.toList());
    }

    protected OrmDomainModel toOrmDomain(NopDynDomain domain) {
        OrmDomainModel ret = new OrmDomainModel();
        ret.setName(domain.getDomainName());
        ret.setDisplayName(domain.getDisplayName());

        StdSqlType stdSqlType = toStdSqlType(domain.getStdSqlType());
        ret.setStdSqlType(stdSqlType);

        if (stdSqlType.isAllowPrecision()) {
            if (domain.getPrecision() == null) {
                ret.setPrecision(1);
            } else {
                ret.setPrecision(domain.getPrecision());
            }
        }

        if (stdSqlType.isAllowScale()) {
            if (domain.getScale() == null) {
                ret.setScale(0);
            } else {
                ret.setScale(domain.getScale());
            }
        }
        return ret;
    }

    protected StdSqlType toStdSqlType(String sqlTypeName) {
        StdSqlType sqlType = StdSqlType.fromStdName(sqlTypeName);
        if (sqlType == null) throw new NopException(ERR_DYN_UNKNOWN_STD_SQL_TYPE).param(ARG_STD_SQL_TYPE, sqlTypeName);
        return sqlType;
    }

    protected void addRelationTables(List<OrmEntityModel> ret, Collection<NopDynEntityMeta> entityMetas,
                                     String basePackageName) {
//        entityMetas.forEach(entityMeta -> {
//            boolean virtualTable = isVirtualTable(entityMeta);
//            entityMeta.getRelationMetasForEntity1().forEach(rel -> {
//                OrmEntityModel relTable = new OrmEntityModel();
//                relTable.setComment(rel.getRemark());
//                forceAddCol(relTable, dynRelationModel.getColumn(NopDynEntityRelation.PROP_NAME_sid, false));
//                if (virtualTable) {
//                    buildVirtualRelationTable(rel, basePackageName);
//                } else {
//                    buildRealRelationTable(rel, basePackageName);
//                }
//                relTable.setName(StringHelper.fullClassName(rel.getRelationName(), basePackageName));
//                if (relTable.getTableName() == null) {
//                    relTable.setTableName(StringHelper.camelCaseToUnderscore(relTable.getShortName(), true));
//                }
//                forceAddCol(relTable, dynRelationModel.getColumn(NopDynEntityRelation.PROP_NAME_entityId1, false));
//                forceAddCol(relTable, dynRelationModel.getColumn(NopDynEntityRelation.PROP_NAME_entityId2, false));
//                addStdColumns(relTable);
//                addJoinRelation(relTable, rel);
//                relTable.setTagSet(TagsHelper.parse(rel.getTagsText(), ','));
//                relTable.addTag(OrmModelConstants.TAG_MANY_TO_MANY);
//                ret.add(relTable);
//            });
//        });
    }


    private OrmEntityModel buildVirtualRelationTable(NopDynEntityRelationMeta rel, String basePackageName) {
        OrmEntityModel relTable = new OrmEntityModel();
        relTable.setClassName(NopDynEntityRelation.class.getName());
        relTable.setTableName(dynRelationModel.getTableName());

//        String entityName1 = StringHelper.fullClassName(rel.getEntityMeta1().getEntityName(), basePackageName);
//        String entityName2 = StringHelper.fullClassName(rel.getEntityMeta2().getEntityName(), basePackageName);
//
//        List<OrmEntityFilterModel> filters = new ArrayList<>();
//        filters.add(OrmEntityFilterModel.of(NopDynEntityRelation.PROP_NAME_entityName1, entityName1));
//        filters.add(OrmEntityFilterModel.of(NopDynEntityRelation.PROP_NAME_entityName2, entityName2));
        return relTable;
    }


    private OrmEntityModel buildRealRelationTable(NopDynEntityRelationMeta rel, String basePackageName) {
        OrmEntityModel relTable = new OrmEntityModel();
        relTable.setClassName(DynamicOrmEntity.class.getName());
        return relTable;
    }

    private void addJoinRelation(OrmEntityModel relTable, NopDynEntityRelationMeta rel) {
//        OrmReferenceModel ref1 = toRelationRefModel(NopDynEntityRelation.PROP_NAME_entityId1,
//                rel.getEntity1PropName(), rel.getEntity1DisplayName());
//        OrmReferenceModel ref2 = toRelationRefModel(NopDynEntityRelation.PROP_NAME_entityId2,
//                rel.getEntity2PropName(), rel.getEntity2DisplayName());
//        relTable.addRelation(ref1);
//        relTable.addRelation(ref2);
    }
}