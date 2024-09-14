/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model.init;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.CaseInsensitiveMap;
import io.nop.commons.collections.IntHashMap;
import io.nop.commons.collections.MutableIntArray;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.OrmAliasModel;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmComponentModel;
import io.nop.orm.model.OrmComponentPropModel;
import io.nop.orm.model.OrmCompositePKModel;
import io.nop.orm.model.OrmComputePropModel;
import io.nop.orm.model.OrmEntityFilterModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmIndexColumnModel;
import io.nop.orm.model.OrmIndexModel;
import io.nop.orm.model.OrmJoinOnModel;
import io.nop.orm.model.OrmModelConstants;
import io.nop.orm.model.OrmReferenceModel;
import io.nop.orm.model.OrmToOneReferenceModel;
import io.nop.orm.model.OrmUniqueKeyModel;
import io.nop.orm.model.utils.OrmModelHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.nop.orm.model.OrmModelErrors.ARG_COL_CODE;
import static io.nop.orm.model.OrmModelErrors.ARG_COL_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_ENTITY_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_OTHER_PROP_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_PROP_ID;
import static io.nop.orm.model.OrmModelErrors.ARG_PROP_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_REF_NAME;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_ALIAS_MUST_REF_TO_COLUMN_OR_REFERENCE;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_ENTITY_MODEL_NO_PK;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_DUPLICATE_COL_CODE;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_DUPLICATE_PROP;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_DUPLICATE_PROP_ID;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_INVALID_PROP_ID;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_REF_JOIN_MUST_ON_COLUMNS_OR_ID;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_REF_JOIN_NO_CONDITION;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_RELATION_JOIN_IS_EMPTY;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_PROP_ID_IS_RESERVED;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_UNKNOWN_COLUMN;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_UNKNOWN_PROP;

public class OrmEntityModelInitializer {
    static final Logger LOG = LoggerFactory.getLogger(OrmEntityModelInitializer.class);
    private final OrmEntityModel entityModel;

    MutableIntArray eagerLoadProps;
    MutableIntArray allPropIds;
    MutableIntArray minLazyLoadProps;
    IEntityPropModel idProp;
    List<OrmColumnModel> pkColumns;
    String[] pkColumnNames;

    int maxPropId;
    OrmColumnModel[] colsByPropId;

    int shardPropId;
    int versionPropId;
    int tenantPropId;

    int nopRevTypePropId;
    int nopRevBeginVerPropId;
    int nopRevEndVerPropId;
    int nopRevExtChangePropId;

    int nopFlowIdPropId;

    int deleteFlagPropId;
    int deleteVersionPropId;
    int createrPropId;
    int createTimePropId;
    int updaterPropId;
    int updateTimePropId;

    Map<String, IEntityPropModel> props = new HashMap<>();
    Map<String, OrmColumnModel> colsByCode = new CaseInsensitiveMap<>();

    Map<String, IEntityPropModel> propsByUnderscoreName = new HashMap<>();

    boolean globalUniqueId;

    public OrmEntityModelInitializer(OrmEntityModel entityModel) {
        this.entityModel = entityModel;
        this.entityModel.setName(entityModel.getName().intern());
        this.entityModel.setTableName(entityModel.getTableName().intern());

        this.entityModel.setQuerySpace(OrmModelHelper.normalizeQuerySpace(entityModel.getQuerySpace()));

        if (entityModel.getClassName() == null)
            entityModel.setClassName(entityModel.getName());

        initPropIds();
        initColMap();
        addInternalProps();
        initProps();
        initAliases();
        initComputes();
        initComponents();
        initIdProp();
        initRelations();
        checkPropNames();

        this.globalUniqueId = entityModel.containsTag(OrmModelConstants.TAG_GID);

        if (tenantPropId > 0 && !globalUniqueId) {
            // 如果主键定义中已经明确包含了租户id，则id必然是跨租户唯一的
            this.globalUniqueId = this.pkColumns.contains(colsByPropId[tenantPropId]);
        }

        // 没有主键肯定不支持修改
        if (entityModel.isNoPrimaryKey())
            entityModel.setReadonly(true);

        for (OrmColumnModel prop : entityModel.getColumns()) {
            String underscoreName = StringHelper.camelCaseToUnderscore(prop.getName(), true).intern();
            this.propsByUnderscoreName.put(underscoreName.toUpperCase(Locale.ROOT), prop);
            IEntityPropModel oldProp = this.propsByUnderscoreName.put(underscoreName, prop);
            if (oldProp != null && oldProp != prop) {
                LOG.info("nop.orm.underscore-name-conflicted:propName={},entityName={}", underscoreName, entityModel.getName());
            }

            // 没有主键的情况下所有字段都必须是非lazy的
            if (prop.isLazy() && entityModel.isNoPrimaryKey()) {
                LOG.warn("nop.warn.column-must-be-non-lazy-when-table-has-no-pk:entityName={},col={}", entityModel.getName(), prop.getName());
                prop.setLazy(false);
            }
        }
    }

    public MutableIntArray getEagerLoadProps() {
        return eagerLoadProps;
    }

    public MutableIntArray getAllPropIds() {
        return allPropIds;
    }

    public MutableIntArray getMinLazyLoadProps() {
        return minLazyLoadProps;
    }

    public Map<String, IEntityPropModel> getPropsByUnderscoreName() {
        return propsByUnderscoreName;
    }

    public IEntityPropModel getIdProp() {
        return idProp;
    }

    public List<OrmColumnModel> getPkColumns() {
        return pkColumns;
    }

    public String[] getPkColumnNames() {
        return pkColumnNames;
    }

    public int getMaxPropId() {
        return maxPropId;
    }

    public OrmColumnModel[] getColsByPropId() {
        return colsByPropId;
    }

    public int getShardPropId() {
        return shardPropId;
    }

    public int getVersionPropId() {
        return versionPropId;
    }

    public int getTenantPropId() {
        return tenantPropId;
    }

    public int getNopRevTypePropId() {
        return nopRevTypePropId;
    }

    public int getNopRevBeginVerPropId() {
        return nopRevBeginVerPropId;
    }

    public int getNopRevEndVerPropId() {
        return nopRevEndVerPropId;
    }

    public int getNopRevExtChangePropId() {
        return nopRevExtChangePropId;
    }

    public int getNopFlowIdPropId() {
        return nopFlowIdPropId;
    }

    public int getDeleteFlagPropId() {
        return deleteFlagPropId;
    }

    public int getDeleteVersionPropId() {
        return deleteVersionPropId;
    }

    public int getCreaterPropId() {
        return createrPropId;
    }

    public int getCreateTimePropId() {
        return createTimePropId;
    }

    public int getUpdaterPropId() {
        return updaterPropId;
    }

    public int getUpdateTimePropId() {
        return updateTimePropId;
    }

    public Map<String, IEntityPropModel> getProps() {
        return props;
    }

    public Map<String, OrmColumnModel> getColsByCode() {
        return colsByCode;
    }

    public boolean isGlobalUniqueId() {
        return globalUniqueId;
    }

    void initIdProp() {
        if (pkColumns.isEmpty()) {
            if (entityModel.isNoPrimaryKey())
                return;

            throw new NopException(ERR_ORM_ENTITY_MODEL_NO_PK).source(entityModel).param(ARG_ENTITY_NAME,
                    entityModel.getName());
        }

        entityModel.setNoPrimaryKey(false);

        if (pkColumns.size() == 1) {
            idProp = pkColumns.get(0);
        } else {
            idProp = new OrmCompositePKModel(entityModel, pkColumns);
        }

        IEntityPropModel prop = props.put(OrmModelConstants.PROP_ID, idProp);
        if (prop != null && prop != idProp) {
            throw new NopException(ERR_ORM_PROP_ID_IS_RESERVED).source(prop).param(ARG_ENTITY_NAME,
                    entityModel.getName());
        }
    }

    /**
     * 为每一列分配一个唯一的propId。如果配置文件中已经指定了propId，则以指定的值为准。
     */
    private void initPropIds() {
        IntHashMap<OrmColumnModel> cols = new IntHashMap<>();

        // 如果是delta模型，则可能设置minPropId，避免与基类中已有的属性冲突
        int maxPropId = ConvertHelper.toPrimitiveInt(entityModel.prop_get(OrmModelConstants.EXT_PROP_MIN_PROP_ID), 0, NopException::new);

        for (OrmColumnModel col : entityModel.getColumns()) {
            col.setOwnerEntityModel(entityModel);
            col.setName(col.getName().intern());
            col.setCode(col.getCode().intern());

            int propId = col.getPropId();
            if (propId <= 0 || propId >= OrmModelConstants.MAX_PROP_ID)
                throw new NopException(ERR_ORM_MODEL_INVALID_PROP_ID).source(col)
                        .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, col.getName())
                        .param(ARG_PROP_ID, propId);

            OrmColumnModel old = cols.put(propId, col);
            if (old != null)
                throw new NopException(ERR_ORM_MODEL_DUPLICATE_PROP_ID).source(col)
                        .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, col.getName())
                        .param(ARG_OTHER_PROP_NAME, old.getName()).param(ARG_PROP_ID, propId);
            maxPropId = Math.max(maxPropId, propId);
        }

        Collections.sort(entityModel.getColumns(), Comparator.comparing(OrmColumnModel::getPropId));

        this.maxPropId = maxPropId;
    }

    void initColMap() {
        for (OrmColumnModel col : entityModel.getColumns()) {
            props.put(col.getName(), col);
            addToColByCode(col);
        }
    }

    public boolean hasOneToOneRelation() {
        for (IEntityRelationModel rel : entityModel.getRelations()) {
            if (rel.isOneToOne())
                return true;
        }
        return false;
    }

    private void addToPropMap(IEntityPropModel prop) {
        IEntityPropModel old = props.put(prop.getName(), prop);
        if (old != null) {
            throw new NopException(ERR_ORM_MODEL_DUPLICATE_PROP).source(prop)
                    .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, prop.getName());
        }
    }

    private void addToColByCode(OrmColumnModel col) {
        OrmColumnModel old = colsByCode.put(col.getCode(), col);
        if (old != null) {
            throw new NopException(ERR_ORM_MODEL_DUPLICATE_COL_CODE).source(col)
                    .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, col.getName())
                    .param(ARG_OTHER_PROP_NAME, old.getName()).param(ARG_COL_CODE, col.getCode());
        }
    }

    private void addInternalProps() {
        if (entityModel.isUseTenant()) {
            OrmColumnModel col;
            String prop = entityModel.getTenantProp();
            if (prop != null) {
                col = entityModel.getColumn(prop);
                if (col == null)
                    throw new NopException(ERR_ORM_UNKNOWN_COLUMN).source(entityModel)
                            .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, prop);
            } else {
                entityModel.setTenantProp(OrmModelConstants.PROP_NAME_nopTenantId);
                col = addColumn(OrmModelConstants.PROP_NAME_nopTenantId, StdSqlType.VARCHAR, 32);
                col.setDefaultValue("0");
                col.setMandatory(true);
            }
            tenantPropId = col.getColumnPropId();
        }

        if (entityModel.isUseRevision()) {
            nopRevTypePropId = addColumn(OrmModelConstants.PROP_NAME_nopRevType, StdSqlType.TINYINT, null).getColumnPropId();
            nopRevBeginVerPropId = addColumn(OrmModelConstants.PROP_NAME_nopRevBeginVer, StdSqlType.BIGINT, null)
                    .getColumnPropId();
            nopRevEndVerPropId = addColumn(OrmModelConstants.PROP_NAME_nopRevEndVer, StdSqlType.BIGINT, null)
                    .getColumnPropId();
            // 一般不需要使用这个属性，除非在模型中主动添加
            nopRevExtChangePropId = getColPropId(OrmModelConstants.PROP_NAME_nopRevExtChange);

            // beginVer必须是主键的一部分，否则增加新版本的时候会出现主键冲突
            entityModel.getColumn(OrmModelConstants.PROP_NAME_nopRevBeginVer).setPrimary(true);
        }

        if (entityModel.isUseShard()) {
            IEntityPropModel col;
            String prop = entityModel.getShardProp();
            if (prop != null) {
                col = entityModel.getColumn(prop);
                if (col == null)
                    throw new NopException(ERR_ORM_UNKNOWN_COLUMN).source(entityModel)
                            .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, prop);
            } else {
                entityModel.setShardProp(OrmModelConstants.PROP_NAME_nopShard);
                col = addColumn(OrmModelConstants.PROP_NAME_nopShard, StdSqlType.VARCHAR, 32);
            }
            shardPropId = col.getColumnPropId();
        }

        if (entityModel.isUseWorkflow()) {
            nopFlowIdPropId = addColumn(OrmModelConstants.PROP_NAME_nopFlowId, StdSqlType.VARCHAR, 32).getColumnPropId();
        }

        this.versionPropId = getColPropId(entityModel.getVersionProp());

        this.createrPropId = getColPropId(entityModel.getCreaterProp());
        this.createTimePropId = getColPropId(entityModel.getCreateTimeProp());
        this.updaterPropId = getColPropId(entityModel.getUpdaterProp());
        this.updateTimePropId = getColPropId(entityModel.getUpdateTimeProp());
        this.deleteFlagPropId = getColPropId(entityModel.getDeleteFlagProp());
        this.deleteVersionPropId = getColPropId(entityModel.getDeleteVersionProp());

        if (this.deleteFlagPropId <= 0)
            entityModel.setUseLogicalDelete(false);
    }

    private int getColPropId(String propName) {
        if (propName == null)
            return 0;

        OrmColumnModel col = entityModel.getColumn(propName);
        if (col == null)
            throw new NopException(ERR_ORM_UNKNOWN_COLUMN).source(entityModel)
                    .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_COL_NAME, propName);
        return col.getPropId();
    }

    private OrmColumnModel addColumn(String colName, StdSqlType sqlType, Integer precision) {
        if (entityModel.hasColumn(colName))
            return entityModel.getColumn(colName);

        OrmColumnModel col = new OrmColumnModel();
        col.setOwnerEntityModel(entityModel);
        col.setName(colName);
        col.setPropId(++maxPropId);
        col.setCode(StringHelper.camelCaseToUnderscore(colName, false));
        col.setStdSqlType(sqlType);
        col.setStdDataType(sqlType.getStdDataType());
        col.setPrecision(precision);
        entityModel.addColumn(col);
        colsByCode.put(col.getCode(), col);
        props.put(col.getName(), col);
        return col;
    }

    private void initProps() {
        colsByPropId = new OrmColumnModel[maxPropId + 1];
        pkColumns = new ArrayList<>();

        eagerLoadProps = new MutableIntArray(entityModel.getColumns().size());
        allPropIds = new MutableIntArray(entityModel.getColumns().size());

        minLazyLoadProps = new MutableIntArray();

        for (OrmColumnModel col : entityModel.getColumns()) {
            colsByPropId[col.getColumnPropId()] = col;
            if (col.isPrimary()) {
                pkColumns.add(col);
                minLazyLoadProps.add(col.getPropId());
                eagerLoadProps.add(col.getPropId());
                allPropIds.add(col.getPropId());
            }

            if (col.getDefaultValue() == null) {
                if (OrmModelConstants.DOMAIN_BOOL_FLAG.equals(col.getBaseDomain())) {
                    col.setDefaultValue("0");
                }
            }
        }

        // 先增加主键列再增加非主键列，确保主键列总是排在最前面，且顺序与pkColumns集合中的顺序一致
        for (OrmColumnModel col : entityModel.getColumns()) {
            if (!col.isPrimary()) {
                if (!col.isLazy()) {
                    eagerLoadProps.add(col.getPropId());
                }
                allPropIds.add(col.getPropId());
            }
        }

        this.pkColumnNames = buildPkNames();

        if (entityModel.isUseTenant()) {
            minLazyLoadProps.add(tenantPropId);
        }

        if (entityModel.isUseShard()) {
            minLazyLoadProps.add(shardPropId);
        }

        if (entityModel.getVersionProp() != null) {
            minLazyLoadProps.add(versionPropId);
        }

        if (this.eagerLoadProps.size() == this.allPropIds.size())
            this.eagerLoadProps = this.allPropIds;
    }

    String[] buildPkNames() {
        String[] ret = new String[pkColumns.size()];
        for (int i = 0, n = ret.length; i < n; i++) {
            ret[i] = pkColumns.get(i).getName().intern();
        }
        return ret;
    }

    void initAliases() {
        List<OrmAliasModel> aliases = entityModel.getAliases();
        for (OrmAliasModel alias : aliases) {
            alias.setOwnerEntityModel(entityModel);
            alias.setName(alias.getName().intern());
            addToPropMap(alias);
        }
    }

    void initComputes() {
        for (OrmComputePropModel compute : entityModel.getComputes()) {
            compute.setOwnerEntityModel(entityModel);
            compute.setName(compute.getName().intern());
            addToPropMap(compute);
        }
    }

    void initRelations() {
        for (OrmReferenceModel ref : entityModel.getRelations()) {
            ref.setOwnerEntityModel(entityModel);
            ref.setName(ref.getName().intern());

            if (ref.getJoin() == null)
                throw new NopException(ERR_ORM_MODEL_REF_JOIN_NO_CONDITION).source(ref)
                        .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_REF_NAME, ref.getName());

            if (ref.getJoin().isEmpty())
                throw new NopException(ERR_ORM_MODEL_RELATION_JOIN_IS_EMPTY).source(ref)
                        .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_REF_NAME, ref.getName());
            addToPropMap(ref);
            checkRef(ref);

            if (ref.isToOneRelation()) {
                // 如果关联字段包含主键字段，则是1对1关联
                OrmToOneReferenceModel toOne = (OrmToOneReferenceModel) ref;
                if (ref.getColumns().containsAll(pkColumns)) {
                    toOne.setOneToOne(true);
                }
            }
        }
    }

    void initComponents() {
        for (OrmComponentModel comp : entityModel.getComponents()) {
            comp.setOwnerEntityModel(entityModel);
            comp.setName(comp.getName().intern());
            addToPropMap(comp);
        }
    }

    /**
     * 验证所有列名引用正确，并根据列名解析得到对应的ColumnModel对象并设置到对象上作为缓存。
     */
    void checkPropNames() {
        for (OrmAliasModel alias : entityModel.getAliases()) {
            String propPath = alias.getPropPath();
            String name = StringHelper.firstPart(propPath, '.');
            IEntityPropModel prop = props.get(name);
            if (prop == null)
                throw new NopException(ERR_ORM_UNKNOWN_PROP).source(alias).param(ARG_ENTITY_NAME, entityModel.getName())
                        .param(ARG_PROP_NAME, name);

            if (!prop.getKind().isColumn() && !prop.getKind().isRelation() && !prop.getKind().isComponent())
                throw new NopException(ERR_ORM_ALIAS_MUST_REF_TO_COLUMN_OR_REFERENCE).source(alias)
                        .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, name);
        }

        for (OrmComponentModel component : entityModel.getComponents()) {
            for (OrmComponentPropModel prop : component.getProps()) {
                String colName = prop.getColumn();
                prop.setColumn(colName.intern());
                prop.setName(prop.getName().intern());

                OrmColumnModel colModel = entityModel.getColumn(colName);
                if (colModel == null) {
                    colModel = colsByCode.get(colName);
                    if (colModel != null) {
                        prop.setColumn(colName);
                    }
                }
                if (colModel == null)
                    throw new NopException(ERR_ORM_UNKNOWN_COLUMN).source(prop)
                            .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, colName);

                prop.setColumnModel(colModel);
            }
        }

        for (OrmUniqueKeyModel keyModel : entityModel.getUniqueKeys()) {
            List<String> columns = keyModel.getColumns();
            List<OrmColumnModel> cols = new ArrayList<>(columns.size());

            for (int i = 0, n = columns.size(); i < n; i++) {
                String column = columns.get(i);
                OrmColumnModel col = entityModel.getColumn(column);
                if (col == null) {
                    col = colsByCode.get(column);
                    if (col != null) {
                        columns.set(i, col.getName());
                    }
                }
                if (col == null)
                    throw new NopException(ERR_ORM_UNKNOWN_COLUMN).source(keyModel)
                            .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_COL_NAME, column);

                cols.add(col);
            }
            keyModel.setColumnModels(cols);
        }

        for (OrmIndexModel indexModel : entityModel.getIndexes()) {
            for (OrmIndexColumnModel indexCol : indexModel.getColumns()) {
                String name = indexCol.getName();
                OrmColumnModel col = entityModel.getColumn(name);
                if (col == null) {
                    col = colsByCode.get(name);
                    if (col != null) {
                        indexCol.setName(col.getName());
                    }
                }
                if (col == null)
                    throw new NopException(ERR_ORM_UNKNOWN_COLUMN).source(indexCol)
                            .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, name);
                indexCol.setColumnModel(col);
            }
        }

        if (entityModel.hasFilter()) {
            for (OrmEntityFilterModel filter : entityModel.getFilters()) {
                String name = filter.getName();
                IEntityPropModel prop = props.get(name);
                if (prop == null)
                    throw new NopException(ERR_ORM_UNKNOWN_PROP).source(filter).param(ARG_ENTITY_NAME, entityModel.getName())
                            .param(ARG_PROP_NAME, name);

                if (!prop.isColumnModel()) {
                    throw new NopException(ERR_ORM_UNKNOWN_COLUMN).source(filter)
                            .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_COL_NAME, name);
                }

                Object value = prop.getStdDataType().convert(filter.getValue());
                filter.setValue(value);
                filter.setColumn((OrmColumnModel) prop);
            }
        }
    }

    void checkRef(OrmReferenceModel ref) {
        List<OrmColumnModel> cols = new ArrayList<>();

        for (OrmJoinOnModel join : ref.getJoin()) {
            // 只检查leftProp。rightProp的检查需要在OrmModel的init函数中进行。
            String leftProp = join.getLeftProp();
            if (leftProp != null) {
                join.setLeftProp(leftProp.intern());

                IEntityPropModel propModel = props.get(leftProp);
                if (propModel == null) {
                    propModel = colsByCode.get(leftProp);
                    if (propModel != null) {
                        join.setLeftProp(propModel.getName());
                    }
                }
                if (propModel == null)
                    throw new NopException(ERR_ORM_UNKNOWN_PROP).source(ref)
                            .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, leftProp);

                if (!propModel.getKind().isColumn() && !propModel.getKind().isId() && !propModel.getKind().isAlias())
                    throw new NopException(ERR_ORM_MODEL_REF_JOIN_MUST_ON_COLUMNS_OR_ID).source(join)
                            .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_REF_NAME, ref.getName())
                            .param(ARG_PROP_NAME, propModel.getName());

                if (propModel.getColumns() != null) {
                    cols.addAll((List) propModel.getColumns());
                }

                join.setLeftPropModel(propModel);
                if (join.getRightProp() == null) {
                    Object rightValue = join.getRightValue();
                    rightValue = propModel.getStdDataType().convert(rightValue, err -> {
                        return new NopException(err).source(ref).param(ARG_ENTITY_NAME, entityModel.getName())
                                .param(ARG_PROP_NAME, leftProp);
                    });
                    join.setRightValue(rightValue);
                }
            }

            if (join.getLeftProp() == null && join.getRightProp() == null)
                throw new NopException(ERR_ORM_MODEL_REF_JOIN_MUST_ON_COLUMNS_OR_ID).source(join)
                        .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_REF_NAME, ref.getName());
        }

        if (ref.getJoin().size() == 1) {
            OrmJoinOnModel join = ref.getJoin().get(0);
            if (join.getLeftProp() == null || join.getRightProp() == null)
                throw new NopException(ERR_ORM_MODEL_REF_JOIN_MUST_ON_COLUMNS_OR_ID).source(join)
                        .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_REF_NAME, ref.getName());
            ref.setSingleColumnJoin(join);
        }
        ref.setColumns(cols);
    }
}
