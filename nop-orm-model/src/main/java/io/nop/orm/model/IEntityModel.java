/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.IntArray;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.PropPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.nop.orm.model.OrmModelErrors.ARG_ENTITY_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_TAG;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_NO_COL_WITH_TAG;

public interface IEntityModel extends IPdmElement, IOrmDataType {

    String getName();

    String getTableName();

    String getClassName();

    String getDbSchema();

    String getDbCatalog();

    default String getSimpleClassName() {
        return StringHelper.simpleClassName(getClassName());
    }

    default String getClassPackageName() {
        return StringHelper.packageName(getClassName());
    }

    default String getClassPackagePath() {
        return getClassPackageName().replace('.', '/');
    }

    boolean isRegisterShortName();

    default String getShortName() {
        return StringHelper.lastPart(getName(), '.');
    }

    default String getPackageName() {
        return StringHelper.packageName(getName());
    }

    default String getPackagePath() {
        return getPackageName().replace('.', '/');
    }

    String getQuerySpace();

    boolean isReadonly();

    boolean isEntityModeEnabled();

    boolean isKvTable();

    /**
     * 首次加载的属性集合，剩下的属性为延迟加载属性。访问到实体上的任何属性时，都会导致加载所有eager属性。
     */
    IntArray getEagerLoadProps();

    /**
     * 延迟加载时至少需要加载主键以及version属性
     */
    IntArray getMinimumLazyLoadProps();

    IntArray getAllPropIds();

    /**
     * 是否有延迟加载的属性
     */
    boolean hasLazyColumn();

    IEntityPropModel getIdProp();

    default boolean isCompositePk() {
        return getPkColumns().size() > 1;
    }

    /**
     * 得到主键对应的列
     */
    List<? extends IColumnModel> getPkColumns();

    List<String> getPkColumnNames();

    List<? extends IColumnModel> getColumns();

    default IColumnModel getColumnByTag(String tag) {
        for (IColumnModel col : getColumns()) {
            if (col.containsTag(tag))
                return col;
        }
        return null;
    }

    default IColumnModel requireColumnByTag(String tag) {
        IColumnModel col = getColumnByTag(tag);
        if (col == null)
            throw new NopException(ERR_ORM_NO_COL_WITH_TAG)
                    .param(ARG_ENTITY_NAME, getName())
                    .param(ARG_TAG, tag);
        return col;
    }

    default List<IColumnModel> getAllColumnsByTag(String tag) {
        List<IColumnModel> cols = new ArrayList<>();
        for (IColumnModel col : getColumns()) {
            if (col.containsTag(tag))
                cols.add(col);
        }
        return cols;
    }

    default IEntityPropModel getPropByTag(String tag) {
        for (IEntityPropModel prop : getAllProps().values()) {
            if (prop.containsTag(tag))
                return prop;
        }
        return null;
    }

    default List<IEntityPropModel> getAllPropsByTag(String tag) {
        List<IEntityPropModel> ret = new ArrayList<>();
        for (IEntityPropModel prop : getAllProps().values()) {
            if (prop.containsTag(tag))
                ret.add(prop);
        }
        return ret;
    }

    default IEntityPropModel getDisplayPropModel() {
        return getPropByTag(OrmModelConstants.TAG_DISP);
    }

    IColumnModel getColumnByPropId(int propId, boolean ignoreUnknown);

    IColumnModel getColumn(String name, boolean ignoreUnknown);

    IColumnModel getColumnByCode(String code, boolean ignoreUnknown);

    IEntityPropModel getProp(String propName, boolean ignoreUnknown);

    Map<String, IEntityPropModel> getAllProps();

    /**
     * 可以为复合属性指定一个别名，从而对外屏蔽内部关联关系。例如 deptName可以对应于dept.name
     *
     * @param alias 属性别名，内部不应该包含字符DOT
     * @return 复合属性路径
     */
    PropPath getAliasPropPath(String alias);

    List<? extends IEntityAliasModel> getAliases();

    List<? extends IEntityComponentModel> getComponents();

    IEntityComponentModel getComponent(String name);

    List<? extends IComputePropModel> getComputes();

    List<? extends IEntityRelationModel> getRelations();

    default List<? extends IEntityRelationModel> getToOneRelations() {
        return getRelations().stream().filter(rel -> rel.getKind().isToOneRelation()).collect(Collectors.toList());
    }

    default List<? extends IEntityRelationModel> getToManyRelations() {
        return getRelations().stream().filter(rel -> rel.getKind().isToManyRelation()).collect(Collectors.toList());
    }

    IEntityRelationModel getRelation(String name, boolean ignoreUnknown);

    boolean isCheckVersionWhenLazyLoad();

    boolean isUseTenant();

    boolean isUseRevision();

    boolean isUseShard();

    boolean isUseLogicalDelete();

    boolean isUseGlobalCache();

    /**
     * 如果useRevision，这里返回查询最新一条记录所需要的数据列，最后一列必须是nopRevEndVer
     */
    List<? extends IColumnModel> getRevLatestKeyColumns();

    boolean containsTenantIdInPk();

    int getVersionPropId();

    int getTenantPropId();

    int getShardPropId();

    int getDeleteFlagPropId();

    String getDeleteFlagProp();

    int getCreaterPropId();

    int getCreateTimePropId();

    int getUpdaterPropId();

    int getUpdateTimePropId();

    /**
     * 最大的propId+1
     */
    int getPropIdBound();

    Integer getMaxBatchLoadSize();

    String getPersistDriver();

    IColumnModel getShardColumn();

    default IColumnModel getTenantColumn() {
        int tenantPropId = getTenantPropId();
        return tenantPropId <= 0 ? null : getColumnByPropId(tenantPropId, false);
    }

    /**
     * 关联到指定列上的引用对象
     *
     * @param propId 列的propId
     * @return 外键定义中包含指定列的所有引用对象
     */
    List<? extends IEntityRelationModel> getColumnsRefs(int propId);

    int getNopRevTypePropId();

    int getNopRevBeginVerPropId();

    int getNopRevEndVarPropId();

    int getNopRevExtChangePropId();
}