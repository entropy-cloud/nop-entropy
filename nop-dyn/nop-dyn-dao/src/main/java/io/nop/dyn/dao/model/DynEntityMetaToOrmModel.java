package io.nop.dyn.dao.model;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.ReflectionManager;
import io.nop.dao.api.DaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dyn.dao.NopDynDaoConstants;
import io.nop.dyn.dao.entity.NopDynDomain;
import io.nop.dyn.dao.entity.NopDynEntity;
import io.nop.dyn.dao.entity.NopDynEntityMeta;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.dyn.dao.entity.NopDynPropMeta;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmAliasModel;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmDomainModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmJoinOnModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.OrmModelConstants;
import io.nop.orm.model.OrmReferenceModel;
import io.nop.orm.model.OrmToOneReferenceModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static io.nop.dyn.dao.NopDynDaoErrors.ARG_ENTITY_NAME;
import static io.nop.dyn.dao.NopDynDaoErrors.ARG_PROP_MAPPING;
import static io.nop.dyn.dao.NopDynDaoErrors.ARG_PROP_NAME;
import static io.nop.dyn.dao.NopDynDaoErrors.ARG_STD_SQL_TYPE;
import static io.nop.dyn.dao.NopDynDaoErrors.ERR_DYN_UNKNOWN_STD_SQL_TYPE;
import static io.nop.dyn.dao.NopDynDaoErrors.ERR_DYN_VIRTUAL_ENTITY_PK_NOT_SID;
import static io.nop.dyn.dao.NopDynDaoErrors.ERR_DYN_VIRTUAL_ENTITY_PROP_MAPPING_NOT_VALID;

public class DynEntityMetaToOrmModel {
    private final IEntityModel dynEntityModel;

    public DynEntityMetaToOrmModel() {
        this.dynEntityModel = ((IOrmEntityDao<?>) DaoProvider.instance().daoFor(NopDynEntity.class)).getEntityModel();
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
        model.setEntities(toOrmEntityModels(module.getEntityMetas()));

        model.init();
        return model;
    }

    List<OrmEntityModel> toOrmEntityModels(Collection<NopDynEntityMeta> entityMetas) {
        return entityMetas.stream().map(this::toOrmEntityModel).collect(Collectors.toList());
    }

    OrmEntityModel toOrmEntityModel(NopDynEntityMeta entityMeta) {
        OrmEntityModel ret = new OrmEntityModel();
        ret.setName(entityMeta.getEntityName());
        ret.setDisplayName(entityMeta.getDisplayName());
        ret.setTableName(entityMeta.forceGetTableName());
        ret.setTagSet(ConvertHelper.toCsvSet(entityMeta.getTagSet()));

        if (entityMeta.getStoreType() == NopDynDaoConstants.ENTITY_STORE_TYPE_VIRTUAL) {
            buildVirtualEntityModel(ret, entityMeta);
        } else {
            buildRealEntityModel(ret, entityMeta);
        }
        return ret;
    }

    protected void buildRealEntityModel(OrmEntityModel entityModel, NopDynEntityMeta entityMeta) {
        entityMeta.getPropMetas().forEach(propMeta -> {
            entityModel.addColumn(toColumnModel(propMeta));
            if (propMeta.getRefEntityName() != null) {
                entityModel.addRelation(toRefModel(propMeta));
            }
        });
    }

    protected void buildVirtualEntityModel(OrmEntityModel entityModel, NopDynEntityMeta entityMeta) {
        for (NopDynPropMeta propMeta : entityMeta.getPropMetas()) {
            if (Boolean.TRUE.equals(propMeta.getIsPrimary())) {
                if (!propMeta.getPropName().equals(OrmModelConstants.PROP_SID))
                    throw new NopException(ERR_DYN_VIRTUAL_ENTITY_PK_NOT_SID).param(ARG_ENTITY_NAME, entityMeta.getEntityName()).param(ARG_PROP_NAME, propMeta.getPropName());
            }
        }

        entityMeta.getPropMetas().forEach(propMeta -> {
            if (propMeta.getDynPropMapping() != null) {
                if (dynEntityModel.getColumn(propMeta.getDynPropMapping(), true) == null)
                    throw new NopException(ERR_DYN_VIRTUAL_ENTITY_PROP_MAPPING_NOT_VALID)
                            .param(ARG_ENTITY_NAME, entityMeta.getEntityName())
                            .param(ARG_PROP_NAME, propMeta.getPropName())
                            .param(ARG_PROP_MAPPING, propMeta.getDynPropMapping());
                entityModel.addAlias(toAliasModel(propMeta));
            } else {
                OrmAliasModel propModel = toAliasModel(propMeta);
                propModel.setPropPath(buildVirtualPropPath(propModel));
                entityModel.addAlias(propModel);
                if (propMeta.getRefEntityName() != null) {
                    entityModel.addRelation(toRefModel(propMeta));
                }
            }
        });
    }

    protected String buildVirtualPropPath(OrmAliasModel alias) {
        StdDataType type = alias.getType().getStdDataType();
        return "extFields." + alias.getName() + "." + type;
    }

    protected OrmReferenceModel toRefModel(NopDynPropMeta propMeta) {
        OrmToOneReferenceModel ret = new OrmToOneReferenceModel();
        ret.setName(toRelationName(propMeta.getPropName()));
        ret.setDisplayName(toDisplayName(propMeta.getDisplayName()));
        ret.setRefEntityName(propMeta.getRefEntityName());
        ret.setRefPropName(propMeta.getRefPropName());
        ret.setRefDisplayName(propMeta.getRefPropDisplayName());
        ret.setTagSet(StringHelper.parseCsvSet(propMeta.getTagSet()));

        List<OrmJoinOnModel> join = new ArrayList<>(1);
        OrmJoinOnModel joinOn = new OrmJoinOnModel();
        joinOn.setLeftProp(propMeta.getPropName());
        joinOn.setRightProp(OrmModelConstants.PROP_ID);
        join.add(joinOn);
        ret.setJoin(join);
        return ret;
    }

    protected String toRelationName(String propName) {
        if (propName.endsWith("Id")) return propName.substring(0, propName.length() - 2);
        return propName + "Obj";
    }

    protected String toDisplayName(String displayName) {
        if (displayName == null) return null;
        if (displayName.endsWith("ID") || displayName.endsWith("Id"))
            return displayName.substring(0, displayName.length() - 2).trim();
        return displayName;
    }

    protected OrmAliasModel toAliasModel(NopDynPropMeta propMeta) {
        OrmAliasModel ret = new OrmAliasModel();
        ret.setName(propMeta.getPropName());
        ret.setDisplayName(propMeta.getDisplayName());
        ret.setTagSet(StringHelper.parseCsvSet(propMeta.getTagSet()));

        StdSqlType sqlType = toStdSqlType(propMeta.getStdSqlType());
        ret.setType(ReflectionManager.instance().buildRawType(sqlType.getStdDataType().getJavaClass()));
        ret.setPropPath(propMeta.getDynPropMapping());
        return ret;
    }

    protected OrmColumnModel toColumnModel(NopDynPropMeta propMeta) {
        OrmColumnModel ret = new OrmColumnModel();
        ret.setName(propMeta.getPropName());
        ret.setCode(StringHelper.camelCaseToUnderscore(propMeta.getPropName(), false));
        Set<String> tagSet = StringHelper.parseCsvSet(propMeta.getTagSet());
        ret.setTagSet(tagSet);
        ret.setComment(propMeta.getRemark());

        ret.setMandatory(Boolean.TRUE.equals(propMeta.getIsMandatory()));
        ret.setPrimary(Boolean.TRUE.equals(propMeta.getIsPrimary()));

        ret.setDefaultValue(propMeta.getDefaultValue());
        ret.setStdDomain(propMeta.getStdDomainName());

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
}