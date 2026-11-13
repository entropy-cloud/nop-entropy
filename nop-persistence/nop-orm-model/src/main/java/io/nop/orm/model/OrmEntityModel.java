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
import io.nop.commons.collections.MutableIntArray;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.TagsHelper;
import io.nop.commons.util.objects.PropPath;
import io.nop.orm.model._gen._OrmEntityModel;
import io.nop.orm.model.init.OrmEntityModelInitializer;

import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * Plan 2380：把继承自 {@code _OrmEntityModel} 的 {@code getUniqueKeys()} 归一为接口契约的
     * {@link List<List<String>>} 形态（每个唯一键一组 prop 名）。
     *
     * <p>{@code getColumns()} 返回的是 prop 名（由 {@code OrmEntityModelInitializer} 归一），
     * 与 {@link #getPkColumnNames()} 同域。刻意<b>不</b>使用 {@code getColumnModels()}：它是手写类上的
     * 瞬态字段，<b>不在 {@code _OrmUniqueKeyModel.copyTo()} 的复制集内</b>，因此
     * clone / JSON 反序列化 / 缓存装载路径上恒为 null —— 用它会让这些路径上的唯一键校验静默失效。
     * {@code getColumns()} 为 null（声明了 {@code <unique-key>} 却未给 {@code <columns>}）时跳过该键。
     */
    @Override
    public List<List<String>> getUniqueKeyPropNames() {
        List<OrmUniqueKeyModel> keys = getUniqueKeys();
        if (keys == null || keys.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        List<List<String>> ret = new ArrayList<>(keys.size());
        for (OrmUniqueKeyModel key : keys) {
            List<String> columns = key.getColumns();
            if (columns == null || columns.isEmpty()) {
                continue;
            }
            ret.add(Collections.unmodifiableList(new ArrayList<>(columns)));
        }
        return ret;
    }
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
    private int deleteVersionPropId;
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

    /**
     * 2.3-C1-016（plan 2552 裁定 I，方向一最小修）：volatile——运行期 topo 重建写点
     * （OrmModelTopEntryBuilder:35）对冻结实体模型的无同步写与 persist 驱动读点
     * （JdbcEntityPersistDriver:240/:254、BatchActionQueueImpl:253）之间 HB 闭合。
     * 方向二（伴生视图根治）= watch-only（plan 2552 Deferred）。
     */
    private volatile boolean dependByOtherEntity;

    private Map<String, IEntityPropModel> props;
    private Map<String, OrmColumnModel> colsByCode;

    private Map<String, IEntityPropModel> propsByUnderscoreName;

    private boolean tenantModel;

    // 2.3-C1-019（plan 2552 裁定 L）：volatile——check-then-act 初始化标志的跨线程可见性
    private volatile boolean inited;

    public OrmEntityModel() {
        setCheckVersionWhenLazyLoad(true);
    }

    public String toString() {
        return getClass().getSimpleName() + "[name=" + StringHelper.simpleClassName(getName()) + ",table="
                + getTableName() + "]@" + getLocation();
    }

    @Override
    public boolean isTenantModel() {
        return tenantModel;
    }

    public void setTenantModel(boolean tenantModel) {
        this.tenantModel = tenantModel;
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
    public boolean isDependByOtherEntity() {
        return dependByOtherEntity;
    }

    @Override
    public void setDependByOtherEntity(boolean dependByOtherEntity) {
        this.dependByOtherEntity = dependByOtherEntity;
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
    public int getDeleteVersionPropId() {
        return deleteVersionPropId;
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
        MutableIntArray allPropIds = initializer.getAllPropIds();
        MutableIntArray eagerLoadProps = initializer.getEagerLoadProps();
        this.allPropIds = allPropIds.toImmutable();
        // 初始化器在无 lazy 列时会把 eagerLoadProps 指向 allPropIds 同一实例（别名优化），
        // 转成不可变数组时需保持该别名关系，否则 hasLazyColumn() 的引用比较会恒返回 true
        this.eagerLoadProps = eagerLoadProps == allPropIds ? this.allPropIds : eagerLoadProps.toImmutable();
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
        this.deleteVersionPropId = initializer.getDeleteVersionPropId();

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
        // 2.3-C1-017（plan 2552 裁定 J）：守卫前置——原形态先污染 props 再守卫（冻结模型
        // 被半更新后抛出）。setDependByOtherEntity 显式豁免本守卫族（裁定 J/I：C1-016
        // sanctioned 运行期写点）。
        checkAllowChange();
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

    public String getCode() {
        return getTableName();
    }
}