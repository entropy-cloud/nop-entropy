/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.INeedInit;
import io.nop.commons.collections.ImmutableIntArray;
import io.nop.commons.collections.IntArray;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.TagsHelper;
import io.nop.commons.util.objects.PropPath;
import io.nop.orm.model._gen._OrmEntityModel;
import io.nop.orm.model.init.OrmEntityModelInitializer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.orm.model.OrmModelErrors.ARG_COL_CODE;
import static io.nop.orm.model.OrmModelErrors.ARG_COL_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_ENTITY_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_PROP_ID;
import static io.nop.orm.model.OrmModelErrors.ARG_PROP_NAME;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_UNKNOWN_COLUMN;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_UNKNOWN_COLUMN_CODE;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_UNKNOWN_COLUMN_PROP_ID;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_UNKNOWN_PROP;

public class OrmEntityModel extends _OrmEntityModel implements IEntityModel, INeedInit {
    private ImmutableIntArray eagerLoadProps;
    private ImmutableIntArray allPropIds;
    private ImmutableIntArray minLazyLoadProps;
    private IEntityPropModel idProp;
    private List<String> pkColumnNames;
    private List<OrmColumnModel> pkColumns;
    private List<OrmColumnModel> revLatestKeyColumns;

    private OrmColumnModel[] colsByPropId;

    private int shardPropId;
    private int versionPropId;
    private int tenantPropId;

    private int deleteFlagPropId;
    private int createrPropId;
    private int createTimePropId;
    private int updaterPropId;
    private int updateTimePropId;

    private int nopFlowIdPropId;
    private int nopRevTypePropId;
    private int nopRevBeginVerPropId;
    private int nopRevEndVerPropId;
    private int nopRevExtChangePropId;

    private boolean globalUniqueId;

    private boolean hasOneToOneRelation;

    private Map<String, IEntityPropModel> props;
    private Map<String, OrmColumnModel> colsByCode;

    private Map<String, IEntityPropModel> propsByUnderscoreName;

    private boolean inited;

    public OrmEntityModel() {
        setCheckVersionWhenLazyLoad(true);
    }

    public String toString() {
        return getClass().getSimpleName() + "[name=" + StringHelper.simpleClassName(getName()) + ",table="
                + getTableName() + "]@" + getLocation();
    }

    /**
     * 所有的列都是主键。一般对应于多对多中间表
     */
    public boolean isAllColumnPrimary() {
        if (getPkColumns() == null) {
            for (OrmColumnModel col : getColumns()) {
                if (!col.isPrimary())
                    return false;
            }
            return true;
        }

        return getPkColumns().size() == getColumns().size();
    }

    @Override
    public OrmDataTypeKind getKind() {
        return OrmDataTypeKind.ENTITY;
    }

    @Override
    public StdDataType getStdDataType() {
        return StdDataType.ANY;
    }

    @Override
    public IntArray getEagerLoadProps() {
        return eagerLoadProps;
    }

    @Override
    public IntArray getMinimumLazyLoadProps() {
        return minLazyLoadProps;
    }

    @Override
    public IntArray getAllPropIds() {
        return allPropIds;
    }

    @Override
    public boolean hasLazyColumn() {
        return allPropIds != eagerLoadProps;
    }

    @Override
    public IEntityPropModel getIdProp() {
        return idProp;
    }

    @Override
    public List<OrmColumnModel> getPkColumns() {
        return pkColumns;
    }

    public void setPkColumns(List<OrmColumnModel> cols) {
        checkAllowChange();
        this.pkColumns = cols;
    }

    @Override
    public boolean isGlobalUniqueId() {
        return globalUniqueId;
    }

    @Override
    public List<String> getPkColumnNames() {
        return pkColumnNames;
    }

    @Override
    public OrmColumnModel getColumnByPropId(int propId, boolean ignoreUnknown) {
        OrmColumnModel col = this.colsByPropId[propId];
        if (ignoreUnknown)
            return col;

        if (col == null)
            throw new NopException(ERR_ORM_UNKNOWN_COLUMN_PROP_ID).param(ARG_ENTITY_NAME, getName()).param(ARG_PROP_ID,
                    propId);
        return col;
    }

    @Override
    public IColumnModel getColumn(String name, boolean ignoreUnknown) {
        IColumnModel col = getColumn(name);
        if (ignoreUnknown)
            return col;
        if (col == null)
            throw new NopException(ERR_ORM_UNKNOWN_COLUMN).param(ARG_ENTITY_NAME, getName()).param(ARG_COL_NAME, name);
        return col;
    }

    @Override
    public IColumnModel getColumnByCode(String code, boolean ignoreUnknown) {
        OrmColumnModel col = colsByCode.get(code);
        if (ignoreUnknown)
            return col;
        if (col == null)
            throw new NopException(ERR_ORM_UNKNOWN_COLUMN_CODE).param(ARG_ENTITY_NAME, getName()).param(ARG_COL_CODE,
                    code);
        return col;
    }

    @Override
    public PropPath getAliasPropPath(String alias) {
        IEntityPropModel propModel = getProp(alias, true);
        if (propModel instanceof IEntityAliasModel) {
            return PropPath.parse(propModel.getAliasPropPath());
        }
        return null;
    }

    @Override
    public IEntityRelationModel getRelation(String name, boolean ignoreUnknown) {
        IEntityRelationModel rel = this.getRelation(name);
        if (ignoreUnknown)
            return rel;
        if (rel == null)
            throw new NopException(ERR_ORM_UNKNOWN_PROP).param(ARG_ENTITY_NAME, getName()).param(ARG_PROP_NAME, name);
        return rel;
    }

    @Override
    public List<OrmColumnModel> getRevLatestKeyColumns() {
        return revLatestKeyColumns;
    }

    public void setRevLatestKeyColumns(List<OrmColumnModel> revLatestKeyColumns) {
        this.revLatestKeyColumns = revLatestKeyColumns;
    }

    @Override
    public Map<String, IEntityPropModel> getAllProps() {
        return props;
    }

    @Override
    public IEntityPropModel getProp(String name, boolean ignoreUnknown) {
        IEntityPropModel prop = props.get(name);
        if (ignoreUnknown)
            return prop;

        if (prop == null)
            throw new NopException(ERR_ORM_UNKNOWN_PROP).param(ARG_ENTITY_NAME, getName()).param(ARG_PROP_NAME, name);
        return prop;
    }

    public void addTag(String tag) {
        setTagSet(TagsHelper.add(getTagSet(), tag));
    }

    @Override
    public IEntityPropModel getPropByUnderscoreName(String name) {
        return propsByUnderscoreName.get(name);
    }

    @Override
    public int getVersionPropId() {
        return versionPropId;
    }

    @Override
    public int getTenantPropId() {
        return tenantPropId;
    }

    @Override
    public int getShardPropId() {
        return shardPropId;
    }

    @Override
    public int getPropIdBound() {
        return colsByPropId.length;
    }

    @Override
    public IColumnModel getShardColumn() {
        return colsByPropId[shardPropId];
    }

    @Override
    public List<? extends IEntityRelationModel> getColumnsRefs(int propId) {
        return getColumnByPropId(propId, false).getColumnRefs();
    }

    @Override
    public int getNopRevTypePropId() {
        return nopRevTypePropId;
    }

    @Override
    public int getNopRevBeginVerPropId() {
        return nopRevBeginVerPropId;
    }

    @Override
    public int getNopRevEndVarPropId() {
        return nopRevEndVerPropId;
    }

    @Override
    public int getNopRevExtChangePropId() {
        return nopRevExtChangePropId;
    }

    @Override
    public int getDeleteFlagPropId() {
        return deleteFlagPropId;
    }

    @Override
    public int getCreaterPropId() {
        return createrPropId;
    }

    @Override
    public int getCreateTimePropId() {
        return createTimePropId;
    }

    @Override
    public int getUpdaterPropId() {
        return updaterPropId;
    }

    @Override
    public int getUpdateTimePropId() {
        return updateTimePropId;
    }

    @Override
    public boolean hasOneToOneRelation() {
        return hasOneToOneRelation;
    }

    @Override
    public void init() {
        if (inited)
            return;

        OrmEntityModelInitializer initializer = new OrmEntityModelInitializer(this);
        this.allPropIds = initializer.getAllPropIds().toImmutable();
        this.eagerLoadProps = initializer.getEagerLoadProps().toImmutable();
        this.versionPropId = initializer.getVersionPropId();
        this.tenantPropId = initializer.getTenantPropId();
        this.nopRevTypePropId = initializer.getNopRevTypePropId();
        this.nopRevBeginVerPropId = initializer.getNopRevBeginVerPropId();
        this.nopRevEndVerPropId = initializer.getNopRevEndVerPropId();
        this.nopRevExtChangePropId = initializer.getNopRevExtChangePropId();
        this.shardPropId = initializer.getShardPropId();

        this.createrPropId = initializer.getCreaterPropId();
        this.createTimePropId = initializer.getCreateTimePropId();
        this.updaterPropId = initializer.getUpdaterPropId();
        this.updateTimePropId = initializer.getUpdateTimePropId();
        this.deleteFlagPropId = initializer.getDeleteFlagPropId();

        this.colsByCode = initializer.getColsByCode();
        this.colsByPropId = initializer.getColsByPropId();
        this.minLazyLoadProps = initializer.getMinLazyLoadProps().toImmutable();
        this.pkColumnNames = CollectionHelper.buildImmutableList(initializer.getPkColumnNames());
        this.pkColumns = initializer.getPkColumns();
        this.idProp = initializer.getIdProp();
        this.props = initializer.getProps();
        this.globalUniqueId = initializer.isGlobalUniqueId();
        this.nopFlowIdPropId = initializer.getNopFlowIdPropId();
        this.hasOneToOneRelation = initializer.hasOneToOneRelation();
        this.propsByUnderscoreName = initializer.getPropsByUnderscoreName();

        inited = true;
    }

    @Override
    public int getNopFlowIdPropId() {
        return nopFlowIdPropId;
    }

    public void addProp(IEntityPropModel prop) {
        if (props == null || props.isEmpty()) {
            props = new HashMap<>();
        }
        props.put(prop.getName(), prop);

        if (prop.isRelationModel()) {
            addRelation((OrmReferenceModel) prop);
        } else if (prop.isComponentModel()) {
            addComponent((OrmComponentModel) prop);
        } else if (prop.isComputeModel()) {
            addCompute((OrmComputePropModel) prop);
        } else if (prop.isAliasModel()) {
            addAlias((OrmAliasModel) prop);
        } else if (prop.isColumnModel()) {
            addColumn((OrmColumnModel) prop);
        }
    }

}