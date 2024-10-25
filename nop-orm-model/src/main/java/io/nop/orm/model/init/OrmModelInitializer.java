/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model.init;

import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.CaseInsensitiveMap;
import io.nop.commons.collections.IntHashMap;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.graph.DefaultDirectedGraph;
import io.nop.core.model.graph.DefaultEdge;
import io.nop.core.model.graph.IDirectedGraph;
import io.nop.core.model.graph.TopoEntry;
import io.nop.core.model.graph.TopologicalOrderIterator;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmDomainModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmJoinOnModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.OrmModelConstants;
import io.nop.orm.model.OrmRefSetModel;
import io.nop.orm.model.OrmReferenceModel;
import io.nop.orm.model.OrmToManyReferenceModel;
import io.nop.orm.model.OrmToOneReferenceModel;
import io.nop.orm.model.utils.OrmModelHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static io.nop.orm.model.OrmModelErrors.ARG_ALLOWED_NAMES;
import static io.nop.orm.model.OrmModelErrors.ARG_COL_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_DATA_TYPE;
import static io.nop.orm.model.OrmModelErrors.ARG_DOMAIN;
import static io.nop.orm.model.OrmModelErrors.ARG_DOMAIN_DATA_TYPE;
import static io.nop.orm.model.OrmModelErrors.ARG_ENTITY_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_LOOP_ENTITY_NAMES;
import static io.nop.orm.model.OrmModelErrors.ARG_OTHER_ENTITY_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_OTHER_LOC;
import static io.nop.orm.model.OrmModelErrors.ARG_PROP_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_REF_ENTITY_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_REF_NAME;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_COL_DATA_TYPE_NOT_MATCH_DOMAIN_DEFINITION;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_COL_NO_STD_SQL_TYPE;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_DUPLICATE_ENTITY_SHORT_NAME;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_INVALID_COLUMN_DOMAIN;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_JOIN_COLUMN_COUNT_LESS_THAN_PK_COLUMN_COUNT;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_REF_DEPENDS_CONTAINS_LOOP;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_REF_ENTITY_NO_PROP;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_REF_PROP_NOT_COLUMN;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_REF_UNKNOWN_ENTITY;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_UNKNOWN_COLUMN;

public class OrmModelInitializer {
    static final Logger LOG = LoggerFactory.getLogger(OrmModelInitializer.class);

    private final OrmModel ormModel;
    Map<String, TopoEntry<IEntityModel>> entryMap = new TreeMap<>();
    Map<String, IEntityModel> entityModelByTableMap = new CaseInsensitiveMap<>();
    Map<String, OrmEntityModel> entityMap = new HashMap<>();
    Map<TopoEntry<IEntityModel>, IEntityModel> topoMap = new TreeMap<>();
    Map<String, OrmToManyReferenceModel> collectionMap = new HashMap<>();

    Map<String, OrmEntityModel> underscoreNameMap = new HashMap<>();

    public OrmModelInitializer(OrmModel ormModel) {
        this.ormModel = ormModel;
        initEntities();

        initRefs();
        initTopoMap();
        checkNames();
    }

    public Map<String, OrmEntityModel> getUnderscoreNameMap() {
        return underscoreNameMap;
    }

    public Map<String, TopoEntry<IEntityModel>> getEntryMap() {
        return entryMap;
    }

    public Map<String, IEntityModel> getEntityModelByTableMap() {
        return entityModelByTableMap;
    }

    public Map<String, OrmEntityModel> getEntityMap() {
        return entityMap;
    }

    public Map<TopoEntry<IEntityModel>, IEntityModel> getTopoMap() {
        return topoMap;
    }

    public Map<String, OrmToManyReferenceModel> getCollectionMap() {
        return collectionMap;
    }

    void checkNames() {

    }

    private void initEntities() {
        for (OrmEntityModel entityModel : ormModel.getEntities()) {
            if (!ormModel.isMerged() && !entityModel.frozen())
                syncDomains(entityModel);

            entityModel.init();
            entityModelByTableMap.put(entityModel.getTableName(), entityModel);

            entityMap.put(entityModel.getName(), entityModel);
            if (entityModel.isRegisterShortName()) {
                IEntityModel oldModel = entityMap.put(entityModel.getShortName(), entityModel);
                if (oldModel != null && oldModel != entityModel)
                    throw new NopException(ERR_ORM_MODEL_DUPLICATE_ENTITY_SHORT_NAME).source(entityModel)
                            .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_OTHER_LOC, oldModel.getLocation())
                            .param(ARG_OTHER_ENTITY_NAME, oldModel.getName());
            }

            if (entityModel.isRegisterShortName()) {
                // 只有entityModel的短名字不重复的情况下才支持underscore名称，否则可能会出现重名的问题
                String underscoreName = StringHelper.camelCaseToUnderscore(entityModel.getShortName(), true);
                if (underscoreName.equals(entityModel.getTableName()))
                    underscoreName = entityModel.getTableName();
                // 只支持全大写和全小写
                underscoreNameMap.put(underscoreName, entityModel);
                underscoreNameMap.put(underscoreName.toUpperCase(Locale.ROOT), entityModel);
            }
        }
    }

    private void syncDomains(OrmEntityModel entityModel) {
        for (OrmColumnModel col : entityModel.getColumns()) {
            String domain = col.getDomain();
            if (domain != null) {
                OrmDomainModel domainModel = ormModel.getDomain(domain);
                if (domainModel == null)
                    throw new NopException(ERR_ORM_MODEL_INVALID_COLUMN_DOMAIN).source(col)
                            .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_COL_NAME, col.getName())
                            .param(ARG_DOMAIN, domain).param(ARG_ALLOWED_NAMES, ormModel.keySet_domains());

                if (domainModel.getStdDomain() != null) {
                    if (col.getStdDomain() != null && !Objects.equals(col.getStdDomain(), domainModel.getStdDomain()))
                        LOG.warn(
                                "nop.orm.col-std-domain-not-sync-with-domain-definition:entityName={},col={},"
                                        + ",col.stdDomain={},domain.stdDomain={}",
                                entityModel.getName(), col.getName(), col.getStdDomain(), domainModel.getStdDomain());
                    col.setStdDomain(domainModel.getStdDomain());
                }

                if (domainModel.getStdSqlType() != null) {
                    if (col.getStdSqlType() != null && col.getStdSqlType() != domainModel.getStdSqlType())
                        LOG.warn(
                                "nop.orm.col-std-sql-type-not-sync-with-domain-definition:entityName={},col={},"
                                        + ",col.stdSqlType={},domain.stdSqlType={}",
                                entityModel.getName(), col.getName(), col.getStdSqlType(), domainModel.getStdSqlType());
                    col.setStdSqlType(domainModel.getStdSqlType());
                }

                if (domainModel.getStdDataType() != null) {
                    if (col.getStdDataType() != null && col.getStdDataType() != domainModel.getStdDataType())
                        throw new NopException(ERR_ORM_MODEL_COL_DATA_TYPE_NOT_MATCH_DOMAIN_DEFINITION).source(col)
                                .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_COL_NAME, col.getName())
                                .param(ARG_DATA_TYPE, col.getStdDataType())
                                .param(ARG_DOMAIN_DATA_TYPE, domainModel.getStdDataType());
                    col.setStdDataType(domainModel.getStdDataType());
                }

                if (domainModel.getPrecision() != null && col.getPrecision() == null) {
                    col.setPrecision(domainModel.getPrecision());
                }

                if (domainModel.getScale() != null && col.getScale() == null) {
                    col.setScale(domainModel.getScale());
                }
            }

            if (col.getStdSqlType() == null)
                throw new NopException(ERR_ORM_MODEL_COL_NO_STD_SQL_TYPE).source(col)
                        .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_COL_NAME, col.getName());

            if (col.getStdDataType() == null) {
                col.setStdDataType(col.getStdSqlType().getStdDataType());
            }
        }
    }

    private void initRefs() {
        for (OrmEntityModel entityModel : ormModel.getEntities()) {
            if (entityModel.frozen())
                continue;

            initRef(entityModel);
        }

        for (OrmEntityModel entityModel : ormModel.getEntities()) {
            if (entityModel.frozen())
                continue;

            IntHashMap<List<IEntityRelationModel>> refMap = new IntHashMap<>();
            for (OrmReferenceModel ref : entityModel.getRelations()) {
                if (ref.isToManyRelation()) {
                    OrmToManyReferenceModel toMany = (OrmToManyReferenceModel) ref;
                    String collectionName = OrmModelHelper.buildCollectionName(entityModel.getName(), ref.getName())
                            .intern();
                    toMany.setCollectionName(collectionName);

                    checkRefProp(toMany);
                }
                int[] refPropIds = buildRefPropIds(ref);
                ref.setRefPropIds(refPropIds);

                if (ref.isToOneRelation()) {
                    for (IEntityJoinConditionModel join : ref.getJoin()) {
                        if (join.getLeftPropModel() != null) {
                            IEntityPropModel propModel = join.getLeftPropModel();
                            for (IColumnModel col : propModel.getColumns()) {
                                refMap.computeIfAbsent(col.getPropId(), k -> new ArrayList<>()).add(ref);
                            }
                        }
                    }
                }

                if (ref.containsTag(OrmModelConstants.TAG_CASCADE_DELETE)) {
                    ref.setCascadeDelete(true);
                }
            }

            refMap.forEachEntry((list, propId) -> {
                entityModel.getColumnByPropId(propId, false).setColumnRefs(list);
            });
        }
    }

    private int[] buildRefPropIds(OrmReferenceModel ref) {
        List<IColumnModel> cols = new ArrayList<>(ref.getJoin().size());
        for (OrmJoinOnModel join : ref.getJoin()) {
            if (join.getRightPropModel() != null) {
                cols.addAll(join.getRightPropModel().getColumns());
            }
        }
        return OrmModelHelper.getPropIds(cols);
    }

    private void initRef(OrmEntityModel entityModel) {
        for (OrmReferenceModel ref : entityModel.getRelations()) {
            checkRefPrimary(entityModel, ref);

            OrmEntityModel refEntityModel = ref.getRefEntityModel();

            if (ref.isToManyRelation())
                collectionMap.put(ref.getCollectionName(), (OrmToManyReferenceModel) ref);

            List<OrmJoinOnModel> joins = ref.getJoin();
            for (OrmJoinOnModel join : joins) {
                checkJoin(join, ref);
            }

            String refPropName = ref.getRefPropName();
            if (refPropName != null) {
                ref.setRefPropName(refPropName.intern());

                IEntityPropModel refProp = refEntityModel.getProp(refPropName, true);
                if (refProp == null) {
                    // 反向关联的属性不存在，可以创建
                    boolean toOne = ref.isOneToOne() || ref.isToManyRelation();
                    OrmReferenceModel relRef = createReference(ref, refEntityModel, toOne);
                    refEntityModel.addProp(relRef);
                }
            }
        }
    }

    void checkJoin(OrmJoinOnModel join, OrmReferenceModel ref) {
        IEntityModel entityModel = ref.getOwnerEntityModel();
        IEntityModel refEntity = ref.getRefEntityModel();

        // leftProp已经在OrmModelInitializer中处理。这里只要处理rightProp即可
        String rightPropName = join.getRightProp();
        if (rightPropName != null) {
            join.setRightProp(rightPropName.intern());

            IEntityPropModel rightProp = refEntity.getProp(rightPropName, true);
            if (rightProp == null) {
                rightProp = refEntity.getColumnByCode(rightPropName, true);
                if (rightProp != null) {
                    join.setRightProp(rightProp.getName());
                }
            }
            if (rightProp == null) {
                throw new NopException(ERR_ORM_MODEL_REF_ENTITY_NO_PROP).source(join)
                        .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_REF_NAME, ref.getName())
                        .param(ARG_REF_ENTITY_NAME, ref.getRefEntityName()).param(ARG_PROP_NAME, rightPropName);
            }
            join.setRightPropModel(rightProp);
            if (join.getLeftPropModel() == null) {
                // 将leftValue的类型转换为与rightProp一致。
                Object leftValue = join.getLeftValue();
                leftValue = rightProp.getStdDataType().convert(leftValue, err -> {
                    return new NopException(err).source(join).param(ARG_ENTITY_NAME, entityModel.getName())
                            .param(ARG_REF_NAME, ref.getName()).param(ARG_REF_ENTITY_NAME, ref.getRefEntityName());
                });
                join.setLeftValue(leftValue);
            }
        }
    }

    OrmReferenceModel createReference(OrmReferenceModel ref, OrmEntityModel refEntityModel, boolean toOne) {
        OrmReferenceModel relRef;
        if (toOne) {
            OrmToOneReferenceModel refOne = new OrmToOneReferenceModel();
            refOne.setOneToOne(ref.isOneToOne());
            if (ref instanceof OrmToOneReferenceModel) {
                refOne.setReverseDepends(!ref.isReverseDepends());
            }
            relRef = refOne;
        } else {
            OrmToManyReferenceModel refMany = new OrmToManyReferenceModel();
            if (ref instanceof OrmToOneReferenceModel) {
                OrmToOneReferenceModel toOneRef = (OrmToOneReferenceModel) ref;
                if (toOneRef.getRefSet() != null) {
                    OrmRefSetModel refSet = toOneRef.getRefSet();
                    refMany.setKeyProp(refSet.getKeyProp());
                    refMany.setSort(refSet.getSort());
                }
            }
            relRef = refMany;
        }
        relRef.setLocation(ref.getLocation());
        relRef.setName(ref.getRefPropName());
        relRef.setRefEntityModel(ref.getOwnerEntityModel());
        relRef.setOwnerEntityModel(refEntityModel);

        relRef.setDisplayName(ref.getRefDisplayName());
        relRef.setRefEntityName(ref.getOwnerEntityModel().getName());
        relRef.setRefPropName(ref.getName());

        if (!CollectionHelper.isEmpty(ref.getTagSet())) {
            Set<String> tagSet = new LinkedHashSet<>();
            for (String tag : ref.getTagSet()) {
                if (tag.startsWith(OrmModelConstants.TAG_PREFIX_REF)) {
                    tagSet.add(tag.substring(OrmModelConstants.TAG_PREFIX_REF.length()));
                }
            }
            relRef.setTagSet(tagSet);
        }

        if (!CollectionHelper.isEmpty(ref.prop_names())) {
            for (String name : ref.prop_names()) {
                if (name.startsWith(OrmModelConstants.TAG_PREFIX_REF)) {
                    String relName = name.substring(OrmModelConstants.TAG_PREFIX_REF.length());
                    BeanTool.instance().setProperty(relRef, relName, ref.getExtProp(name));
                }
            }
        }

        List<OrmColumnModel> cols = new ArrayList<>(ref.getJoin().size());
        List<OrmJoinOnModel> joins = ref.getJoin();
        List<OrmJoinOnModel> refJoins = new ArrayList<>(joins.size());

        for (OrmJoinOnModel join : joins) {
            checkJoin(join, ref);

            OrmJoinOnModel refJoin = new OrmJoinOnModel();
            refJoin.setRightProp(join.getLeftProp());
            refJoin.setRightValue(join.getLeftValue());
            refJoin.setRightPropModel(join.getLeftPropModel());
            refJoin.setLeftProp(join.getRightProp());
            refJoin.setLeftValue(join.getRightValue());
            refJoin.setLeftPropModel(join.getRightPropModel());
            refJoins.add(refJoin);
            checkJoin(refJoin, relRef);
            if (refJoin.getLeftPropModel() != null) {
                if (!refJoin.getLeftPropModel().isColumnModel())
                    throw new NopException(ERR_ORM_MODEL_REF_PROP_NOT_COLUMN).loc(join.getLocation())
                            .param(ARG_PROP_NAME, refJoin.getLeftProp())
                            .param(ARG_ENTITY_NAME, refEntityModel.getName());
                cols.add((OrmColumnModel) refJoin.getLeftPropModel());
            }
        }
        relRef.setColumns(cols);
        relRef.setJoin(refJoins);
        if (refJoins.size() == 1) {
            relRef.setSingleColumnJoin(refJoins.get(0));
        }
        return relRef;
    }

    void checkRefProp(OrmToManyReferenceModel ref) {
        String keyProp = ref.getKeyProp();
        OrmEntityModel refEntityModel = ref.getRefEntityModel();
        if (keyProp != null) {
            IColumnModel col = refEntityModel.getColumn(keyProp, true);
            if (col == null) {
                throw new NopException(ERR_ORM_UNKNOWN_COLUMN).loc(ref.getLocation())
                        .param(ARG_COL_NAME, keyProp).param(ARG_ENTITY_NAME, refEntityModel.getName());
            }
        }

        List<OrderFieldBean> sort = ref.getSort();
        if (sort != null) {
            for (OrderFieldBean order : sort) {
                String propName = order.getName();
                IColumnModel col = refEntityModel.getColumn(propName, true);
                if (col == null) {
                    throw new NopException(ERR_ORM_UNKNOWN_COLUMN).loc(ref.getLocation())
                            .param(ARG_COL_NAME, propName).param(ARG_ENTITY_NAME, refEntityModel.getName());
                }
            }
        }
    }

    /**
     * to-one关联必须引用关联实体的主键
     */
    void checkRefPrimary(OrmEntityModel entityModel, OrmReferenceModel ref) {
        OrmEntityModel refEntityModel = entityMap.get(ref.getRefEntityName());
        if (refEntityModel == null)
            throw new NopException(ERR_ORM_MODEL_REF_UNKNOWN_ENTITY).source(ref)
                    .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_REF_NAME, ref.getName())
                    .param(ARG_REF_ENTITY_NAME, ref.getRefEntityName());

        ref.setRefEntityModel(refEntityModel);

        ref.setDynamicRelation(isDynamicRelation(ref));

        if (ref.isToOneRelation()) {
            // 必须引用对象的主键
            int rightPropCount = 0;
            for (OrmJoinOnModel join : ref.getJoin()) {
                if (join.getRightProp() != null) {
                    rightPropCount++;
                    OrmColumnModel col = refEntityModel.getColumn(join.getRightProp());
                    if (col == null && OrmModelConstants.PROP_ID.equals(join.getRightProp())) {
                        col = refEntityModel.getIdProp().isColumnModel() ? (OrmColumnModel) refEntityModel.getIdProp() : null;
                    }
                    if (col == null)
                        throw new NopException(ERR_ORM_MODEL_REF_ENTITY_NO_PROP).loc(join.getLocation())
                                .param(ARG_PROP_NAME, join.getRightProp()).param(ARG_REF_ENTITY_NAME, refEntityModel.getName());
                    if (!col.isPrimary())
                        ref.setJoinOnNonPkColumn(true);
                    join.setRightPropModel(col);
                }
            }

            if (!ref.isJoinOnNonPkColumn()) {
                if (rightPropCount != refEntityModel.getPkColumns().size())
                    throw new NopException(ERR_ORM_MODEL_JOIN_COLUMN_COUNT_LESS_THAN_PK_COLUMN_COUNT).source(ref)
                            .param(ARG_ENTITY_NAME, entityModel.getName())
                            .param(ARG_REF_ENTITY_NAME, refEntityModel.getName()).param(ARG_PROP_NAME, ref.getName());

                // 确保join字段的顺序按照关联表主键字段的顺序排列
                if (ref.getJoin().size() > 1 && !isRefColAligned(ref.getJoin(), refEntityModel.getPkColumns())) {
                    List<OrmJoinOnModel> ordered = new ArrayList<>(ref.getJoin().size());
                    for (IColumnModel col : refEntityModel.getPkColumns()) {
                        ordered.add(findJoinByRefCol(ref.getJoin(), col));
                    }
                    // 最后加入固定值条件
                    for (OrmJoinOnModel join : ref.getJoin()) {
                        if (join.getRightPropModel() == null)
                            ordered.add(join);
                    }
                    ref.setJoin(ordered);
                }
            }
        }
    }

    private boolean isDynamicRelation(IEntityRelationModel rel) {
        if(rel.isDynamicRelation())
            return true;

        // 左实体没有租户，它关联的实体如果有租户，则不能缓存
        if (rel.getOwnerEntityModel().getTenantPropId() <= 0) {
            return rel.getRefEntityModel().getTenantPropId() > 0;
        }
        return false;
    }

    private boolean isRefColAligned(List<? extends IEntityJoinConditionModel> join, List<? extends IColumnModel> cols) {
        if(join.size()  != cols.size())
            return false;

        for (int i = 0, n = join.size(); i < n; i++) {
            if (join.get(i).getRightPropModel() != cols.get(i))
                return false;
        }
        return true;
    }

    private OrmJoinOnModel findJoinByRefCol(List<OrmJoinOnModel> join, IColumnModel refCol) {
        for (OrmJoinOnModel on : join) {
            if (on.getRightPropModel() == refCol)
                return on;
        }
        throw new IllegalStateException("invalid join prop");
    }

    private void initTopoMap() {
        IDirectedGraph<IEntityModel, DefaultEdge<IEntityModel>> graph = buildDependsMap();
        for (IEntityModel entityModel : graph.vertexSet()) {
            if (graph.getOutwardDegree(entityModel) != 0) {
                entityModel.setDependByOtherEntity(true);
            }
        }

        TopologicalOrderIterator<IEntityModel> it = graph.topologicalOrderIterator(false);
        int topoOrder = 0;
        while (it.hasNext()) {
            TopoEntry<IEntityModel> entry = new TopoEntry<>(topoOrder++, it.next());
            IEntityModel entityModel = entry.getValue();
            entryMap.put(entityModel.getName(), entry);
            topoMap.put(entry, entityModel);
        }

        List<String> tableNames = topoMap.values().stream().map(IEntityModel::getTableName).collect(Collectors.toList());
        LOG.debug("nop.orm.entity-topology-order:model={},tables={}", ormModel.getLocation(), tableNames);

        if (!it.getRemaining().isEmpty()) {
            Set<String> names = it.getRemaining().stream().map(IEntityModel::getName).collect(Collectors.toSet());
            throw new NopException(ERR_ORM_MODEL_REF_DEPENDS_CONTAINS_LOOP).param(ARG_LOOP_ENTITY_NAMES, names);
        }
    }

    private IDirectedGraph<IEntityModel, DefaultEdge<IEntityModel>> buildDependsMap() {
        DefaultDirectedGraph<IEntityModel, DefaultEdge<IEntityModel>> graph = DefaultDirectedGraph.create();
        for (IEntityModel entityModel : ormModel.getEntities()) {
            graph.addVertex(entityModel);
            for (IEntityRelationModel rel : entityModel.getRelations()) {
                if (rel.isToOneRelation()) {
                    // 忽略自关联
                    if (entityModel.getName().equals(rel.getRefEntityName()))
                        continue;

                    // 忽略对于视图的依赖
                    if (rel.getRefEntityModel().isTableView())
                        continue;

                    // 如果指定了忽略关联依赖
                    OrmToOneReferenceModel toOne = (OrmToOneReferenceModel) rel;
                    if (toOne.isIgnoreDepends())
                        continue;

                    if (!rel.isReverseDepends()) {
                        IEntityModel refEntityModel = entityMap.get(rel.getRefEntityName());
                        // 子表依赖主表
                        graph.addEdge(refEntityModel, entityModel);
                    }
                }
            }
        }
        return graph;
    }
}