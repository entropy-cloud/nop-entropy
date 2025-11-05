/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dbtool.core.diff;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.collections.KeyedList;
import io.nop.commons.diff.DiffType;
import io.nop.commons.diff.IDiffValue;
import io.nop.commons.util.StringHelper;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.utils.DaoHelper;
import io.nop.dbtool.core.DataBaseMeta;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.OrmUniqueKeyModel;

/**
 * 根据 ORM 模型结构，对比生成相应的 SQL 脚本：<pre>
 * ```java
 * List&lt;OrmDbDiffer.DiffDdl> diffDdlList =
 *          OrmDbDiffer.forDialect("h2")
 *                      .genDiffDdl(oldOrmModel, newOrmModel);
 * String sql = OrmDbDiffer.DiffDdl.toSql(diffDdlList);
 * ```
 * </pre>
 *
 * 注意，当前技术条件下，对数据库的自动升级存在一定的数据安全风险，
 * 一般建议先通过 {@link #genDiffDdl} 自动生成大部分的 DDL 脚本，
 * 然后，再人工进行核对校正，特别是对于字段重命名、字段数据转换等的处理。
 * <p/>
 * 自动生成的升级脚本**不能识别重命名**，只能采用先删除再新增的模式，因此，
 * 需要在删除前手工添加数据迁移逻辑，或者将删增脚本替换为重命名脚本。
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-04-26
 */
public class OrmDbDiffer {
    private final IDialect dialect;
    private final DdlSqlCreator ddlSqlCreator;

    public static OrmDbDiffer forDialect(String dialectName) {
        return forDialect(DialectManager.instance().getDialect(dialectName));
    }

    public static OrmDbDiffer forDialect(IDialect dialect) {
        return new OrmDbDiffer(dialect);
    }

    private OrmDbDiffer(IDialect dialect) {
        this.dialect = dialect;
        this.ddlSqlCreator = DdlSqlCreator.forDialect(dialect);
    }

    /**
     * 生成数据库 `oldDbMeta` 的 ORM 模型变更成为 `newOrmModel` 所需执行的 DDL 脚本（按先增再改后删的顺序排序）。
     * <p/>
     * `oldDbMeta` 通过 `JdbcMetaDiscovery#discover` 从数据库中根据匹配的表结构而生成：<pre>
     * ```java
     * JdbcMetaDiscovery discovery = JdbcMetaDiscovery.forDataSource(dataSource);
     * DataBaseMeta oldDbMeta = discovery.discover(null, null, "DEV_%");
     * ```
     * </pre>
     * 而 `newOrmModel` 可直接通过 `DslModelHelper#loadDslModel` 加载 `app.orm.xml` 得到。
     *
     * @return 无差异时，返回空集合
     */
    public List<DiffDdl> genDiffDdl(DataBaseMeta oldDbMeta, OrmModel newOrmModel) {
        return genDiffDdl(oldDbMeta.getOrmModel(), newOrmModel);
    }

    /**
     * 对比新旧 ORM Entity 之间的差异，并生成从 `oldOrmModelEntities` 变更成为
     * `newOrmModelEntities` 所需执行的 DDL 脚本（按先增再改后删的顺序排序）。
     * <p/>
     * 在多数据源的场景下，可先根据 {@link IEntityModel#getQuerySpace()}
     * 收集相同库中的待比对 Entity，再依次对数据源进行升级等处理。
     */
    public List<DiffDdl> genDiffDdl(
            List<? extends IEntityModel> oldOrmModelEntities, List<? extends IEntityModel> newOrmModelEntities
    ) {
        OrmModel oldOrmModel = createOrmModelByEntities(oldOrmModelEntities);
        OrmModel newOrmModel = createOrmModelByEntities(newOrmModelEntities);

        return genDiffDdl(oldOrmModel, newOrmModel);
    }

    /**
     * 对比新旧 ORM 模型之间的差异，并生成从 `oldOrmModel` 变更成为 `newOrmModel` 所需执行的 DDL 脚本（按先增再改后删的顺序排序）。
     * <p/>
     * 注：`oldOrmModel` 与 `newOrmModel` 均可直接通过 `DslModelHelper#loadDslModel`
     * 加载对应的 `app.orm.xml` 得到。
     *
     * @return 无差异时，返回空集合
     */
    public List<DiffDdl> genDiffDdl(OrmModel oldOrmModel, OrmModel newOrmModel) {
        IDiffValue ormModelDiff = new OrmModelDiffer().diffTables(dialect, oldOrmModel, newOrmModel);

        IDiffValue entitiesDiff = getPropDiff(ormModelDiff, "entities");
        if (entitiesDiff == null) {
            return new ArrayList<>();
        }

        Map<DiffType, List<DiffDdl>> resultsMap = new HashMap<>();

        // Note：在对比的数据是集合时，若某一方为空或 null，则差异类别为 replace，即，用一方直接替换另一方
        if (entitiesDiff.getDiffType() == DiffType.replace) {
            if (entitiesDiff.getOldValue() != null) {
                ((KeyedList<IEntityModel>) entitiesDiff.getOldValue()).forEach((oldEntity) -> {
                    DiffDdl[] diffDdls = createEntityDiffDdls(DiffType.remove, oldEntity, null);
                    addDiffDdlsToMap(resultsMap, diffDdls);
                });
            }

            if (entitiesDiff.getNewValue() != null) {
                ((KeyedList<IEntityModel>) entitiesDiff.getNewValue()).forEach((newEntity) -> {
                    DiffDdl[] diffDdls = createEntityDiffDdls(DiffType.add, null, newEntity);
                    addDiffDdlsToMap(resultsMap, diffDdls);
                });
            }
        } else {
            entitiesDiff.getKeyedElementDiffs().forEach((entityName, entityDiff) -> {
                DiffType diffType = entityDiff.getDiffType();
                IEntityModel oldEntity = (IEntityModel) entityDiff.getOldValue();
                IEntityModel newEntity = (IEntityModel) entityDiff.getNewValue();

                if (diffType != DiffType.update) {
                    DiffDdl[] diffDdls = createEntityDiffDdls(diffType, oldEntity, newEntity);
                    addDiffDdlsToMap(resultsMap, diffDdls);
                } else {
                    List<DiffDdl> results = resultsMap.computeIfAbsent(diffType, k -> new ArrayList<>());

                    IDiffValue uniqueKeysDiff = getPropDiff(entityDiff, "uniqueKeys");
                    Map<DiffType, List<DiffDdl>> uniqueKeysDiffDdl = createUniqueKeysDiffDdl(newEntity, uniqueKeysDiff);

                    IDiffValue columnsDiff = getPropDiff(entityDiff, "columns");
                    Map<DiffType, List<DiffDdl>> columnsDiffDdl = createColumnsDiffDdl(newEntity, columnsDiff);

                    results.addAll(columnsDiffDdl.getOrDefault(DiffType.add, new ArrayList<>()));
                    // 先删除唯一键
                    results.addAll(uniqueKeysDiffDdl.getOrDefault(DiffType.remove, new ArrayList<>()));
                    // 再删除/修改字段
                    results.addAll(columnsDiffDdl.getOrDefault(DiffType.remove, new ArrayList<>()));
                    results.addAll(columnsDiffDdl.getOrDefault(DiffType.update, new ArrayList<>()));
                    // 最后添加唯一键
                    results.addAll(uniqueKeysDiffDdl.getOrDefault(DiffType.add, new ArrayList<>()));
                }
            });
        }

        List<DiffDdl> results = new ArrayList<>();
        // 先新增表
        results.addAll(resultsMap.getOrDefault(DiffType.add, new ArrayList<>()));
        // 再修改表
        results.addAll(resultsMap.getOrDefault(DiffType.update, new ArrayList<>()));
        // 最后删除表
        results.addAll(resultsMap.getOrDefault(DiffType.remove, new ArrayList<>()));

        return results;
    }

    private OrmModel createOrmModelByEntities(List<? extends IEntityModel> entities) {
        OrmModel ormModel = new OrmModel();

        if (entities != null) {
            entities.forEach((entity) -> ormModel.addEntity((OrmEntityModel) entity));
        }
        return ormModel;
    }

    private Map<DiffType, List<DiffDdl>> createColumnsDiffDdl(
            IEntityModel newEntityModel, IDiffValue columnsDiff
    ) {
        // Note：不处理表字段未定义的情况，不允许表为空结构
        if (columnsDiff == null) {
            return new HashMap<>();
        }

        // 需要和唯一键约束的增删做排序处理，故而，按变更类型放置 DDL
        Map<DiffType, List<DiffDdl>> resultsMap = new HashMap<>();

        columnsDiff.getKeyedElementDiffs().forEach((columnName, columnDiff) -> {
            if (isUselessDiff(columnDiff)) {
                return;
            }

            DiffType diffType = columnDiff.getDiffType();
            IColumnModel oldColumn = (IColumnModel) columnDiff.getOldValue();
            IColumnModel newColumn = (IColumnModel) columnDiff.getNewValue();

            DiffDdl[] diffDdls = createColumnDiffDdls(newEntityModel, diffType, oldColumn, newColumn);
            addDiffDdlsToMap(resultsMap, diffDdls);
        });

        return resultsMap;
    }

    private Map<DiffType, List<DiffDdl>> createUniqueKeysDiffDdl(
            IEntityModel newEntityModel, IDiffValue uniqueKeysDiff
    ) {
        if (uniqueKeysDiff == null) {
            return new HashMap<>();
        }

        // 需要和字段的增删改做排序处理，故而，按变更类型放置 DDL
        Map<DiffType, List<DiffDdl>> resultsMap = new HashMap<>();

        // Note：在对比的数据是集合时，若某一方为空或 null，则差异类别为 replace，即，用一方直接替换另一方
        if (uniqueKeysDiff.getDiffType() == DiffType.replace) {
            if (uniqueKeysDiff.getOldValue() != null) {
                ((KeyedList<OrmUniqueKeyModel>) uniqueKeysDiff.getOldValue()).forEach((oldUniqueKey) -> {
                    DiffDdl[] diffDdls = createUniqueKeyDiffDdls(newEntityModel, DiffType.remove, oldUniqueKey, null);
                    addDiffDdlsToMap(resultsMap, diffDdls);
                });
            }

            if (uniqueKeysDiff.getNewValue() != null) {
                ((KeyedList<OrmUniqueKeyModel>) uniqueKeysDiff.getNewValue()).forEach((newUniqueKey) -> {
                    DiffDdl[] diffDdls = createUniqueKeyDiffDdls(newEntityModel, DiffType.add, null, newUniqueKey);
                    addDiffDdlsToMap(resultsMap, diffDdls);
                });
            }
        } else {
            uniqueKeysDiff.getKeyedElementDiffs().forEach((uniqueKeyName, uniqueKeyDiff) -> {
                if (isUselessDiff(uniqueKeyDiff)) {
                    return;
                }

                DiffType diffType = uniqueKeyDiff.getDiffType();
                OrmUniqueKeyModel oldUniqueKey = (OrmUniqueKeyModel) uniqueKeyDiff.getOldValue();
                OrmUniqueKeyModel newUniqueKey = (OrmUniqueKeyModel) uniqueKeyDiff.getNewValue();

                DiffDdl[] diffDdls = createUniqueKeyDiffDdls(newEntityModel, diffType, oldUniqueKey, newUniqueKey);
                addDiffDdlsToMap(resultsMap, diffDdls);
            });
        }

        return resultsMap;
    }

    private IDiffValue getPropDiff(IDiffValue diff, String prop) {
        Map<String, ? extends IDiffValue> propDiffs = diff.getPropDiffs();

        return propDiffs != null ? propDiffs.get(prop) : null;
    }

    private void addDiffDdlsToMap(Map<DiffType, List<DiffDdl>> map, DiffDdl... diffDdls) {
        for (DiffDdl diffDdl : diffDdls) {
            map.computeIfAbsent(diffDdl.type, k -> new ArrayList<>()).add(diffDdl);
        }
    }

    /** 检查差异结果是否无用：仅包含不影响 DDL 构造的变更属性 */
    private boolean isUselessDiff(IDiffValue diff) {
        Map<String, ? extends IDiffValue> propDiffs = diff.getPropDiffs();
        if (propDiffs == null) {
            return false;
        }

        Set<String> props = new HashSet<>(propDiffs.keySet());
        if (diff.getOldValue() instanceof IColumnModel) {
            props.remove("name");
            props.remove("stdDataType");

            if (!props.isEmpty() && diff.getDiffType() == DiffType.update) {
                IColumnModel oldColumn = (IColumnModel) diff.getOldValue();
                IColumnModel newColumn = (IColumnModel) diff.getNewValue();

                // 在字段更新时，若新字段的备注属性 displayName 或 comment 不为空，
                // 则按二者的优先级依次删除另一个存在的变更，确保仅高优先级的非空备注属性的变更有效
                if (!StringHelper.isEmpty(newColumn.getDisplayName())) {
                    props.remove("comment");
                    if (newColumn.getDisplayName().equals(oldColumn.getComment())) {
                        props.remove("displayName");
                    }
                } else if (!StringHelper.isEmpty(newColumn.getComment())) {
                    props.remove("displayName");
                    if (newColumn.getComment().equals(oldColumn.getDisplayName())) {
                        props.remove("comment");
                    }
                }
            }
        } else if (diff.getOldValue() instanceof OrmUniqueKeyModel) {
            props.remove("name");
            props.remove("comment");
        }

        return props.isEmpty();
    }

    /** 注意，{@link DiffType#update} 需单独处理 */
    private DiffDdl[] createEntityDiffDdls(DiffType diffType, IEntityModel oldEntity, IEntityModel newEntity) {
        switch (diffType) {
            // 对表的新增
            case add: {
                String sql = ddlSqlCreator.createTable(newEntity);
                return new DiffDdl[] { new DiffDdl(newEntity, diffType, sql) };
            }
            // 对表的删除
            case remove: {
                String sql = ddlSqlCreator.dropTable(oldEntity, true);
                return new DiffDdl[] { new DiffDdl(oldEntity, diffType, sql) };
            }
        }
        return new DiffDdl[0];
    }

    private DiffDdl[] createColumnDiffDdls(
            IEntityModel newEntityModel, DiffType diffType, //
            IColumnModel oldColumn, IColumnModel newColumn
    ) {
        switch (diffType) {
            // 对字段的新增
            case add: {
                String sql = ddlSqlCreator.addColumn(newColumn);
                return new DiffDdl[] { new DiffDdl(newEntityModel, diffType, newColumn, sql) };
            }
            // 对字段的删除
            case remove: {
                String sql = ddlSqlCreator.dropColumn(oldColumn);
                return new DiffDdl[] { new DiffDdl(newEntityModel, diffType, oldColumn, sql) };
            }
            // 对字段的修改
            case update: {
                String sql = ddlSqlCreator.modifyColumn(newColumn, oldColumn);
                return new DiffDdl[] { new DiffDdl(newEntityModel, diffType, newColumn, sql) };
            }
        }
        return new DiffDdl[0];
    }

    private DiffDdl[] createUniqueKeyDiffDdls(
            IEntityModel newEntityModel, DiffType diffType, //
            OrmUniqueKeyModel oldUniqueKey, OrmUniqueKeyModel newUniqueKey
    ) {
        switch (diffType) {
            // 对唯一键的新增
            case add: {
                if (StringHelper.isEmpty(newUniqueKey.getConstraint())) {
                    break;
                }

                String sql = ddlSqlCreator.addUniqueKey(newEntityModel, newUniqueKey);
                return new DiffDdl[] { new DiffDdl(newEntityModel, diffType, newUniqueKey, sql) };
            }
            // 对唯一键的删除
            case remove: {
                if (StringHelper.isEmpty(oldUniqueKey.getConstraint())) {
                    break;
                }

                String sql = ddlSqlCreator.dropUniqueKey(newEntityModel, oldUniqueKey);
                return new DiffDdl[] { new DiffDdl(newEntityModel, diffType, oldUniqueKey, sql) };
            }
            // 唯一键不支持修改，只能先删后增
            case update: {
                if (StringHelper.isEmpty(oldUniqueKey.getConstraint()) //
                    || StringHelper.isEmpty(newUniqueKey.getConstraint())) {
                    break;
                }

                return new DiffDdl[] {
                        new DiffDdl(newEntityModel,
                                    DiffType.remove,
                                    oldUniqueKey,
                                    ddlSqlCreator.dropUniqueKey(newEntityModel, oldUniqueKey)),
                        new DiffDdl(newEntityModel,
                                    DiffType.add,
                                    newUniqueKey,
                                    ddlSqlCreator.addUniqueKey(newEntityModel, newUniqueKey))
                };
            }
        }
        return new DiffDdl[0];
    }

    /** 记录详细的 DDL 变更信息，方便调用方根据变更类型做出进一步处理 */
    @DataBean
    public static class DiffDdl {
        /** 变更类型 */
        private final DiffType type;
        /** DDL 脚本 */
        private final String ddl;

        /** 多数据源的名称，默认为 default */
        private final String querySpace;
        /** 变更目标所在的表名称 */
        private final String targetTable;
        /** 变更目标类型 */
        private DiffDdlTargetType targetType;
        /** 变更目标的名称。在 {@link #targetType} 为 {@link DiffDdlTargetType#table} 时，该值为 null */
        private String target;

        public DiffDdl(IEntityModel entityModel, DiffType type, IColumnModel columnModel, String ddl) {
            this(entityModel, type, ddl);

            this.targetType = DiffDdlTargetType.column;
            this.target = columnModel.getCode();
        }

        public DiffDdl(IEntityModel entityModel, DiffType type, OrmUniqueKeyModel uniqueKeyModel, String ddl) {
            this(entityModel, type, ddl);

            this.targetType = DiffDdlTargetType.uniqueKey;
            this.target = uniqueKeyModel.getConstraint();
        }

        public DiffDdl(IEntityModel entityModel, DiffType type, String ddl) {
            this.type = type;
            this.ddl = ddl;

            this.querySpace = DaoHelper.normalizeQuerySpace(entityModel.getQuerySpace());
            this.targetTable = entityModel.getTableName();
            this.targetType = DiffDdlTargetType.table;
        }

        public DiffType getType() {
            return this.type;
        }

        public String getDdl() {
            return this.ddl;
        }

        public String getQuerySpace() {
            return this.querySpace;
        }

        public DiffDdlTargetType getTargetType() {
            return this.targetType;
        }

        public String getTargetTable() {
            return this.targetTable;
        }

        public String getTarget() {
            return this.target;
        }

        /** @return 若参数为空，则返回 null */
        public static String toSql(List<DiffDdl> diffDdlList) {
            return diffDdlList.isEmpty()
                   ? null
                   : diffDdlList.stream().map(OrmDbDiffer.DiffDdl::getDdl).collect(Collectors.joining(";\n"));
        }
    }

    /** DDL 变更的目标类型 */
    public enum DiffDdlTargetType {
        table,
        column,
        index,
        uniqueKey,
        foreignKey;
    }
}
