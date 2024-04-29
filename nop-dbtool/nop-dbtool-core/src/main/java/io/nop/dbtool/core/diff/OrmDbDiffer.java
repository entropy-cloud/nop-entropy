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
import java.util.List;
import java.util.Map;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.diff.DiffType;
import io.nop.commons.diff.IDiffValue;
import io.nop.dbtool.core.DataBaseMeta;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.OrmUniqueKeyModel;

/**
 * 根据 ORM 模型结构，对比生成相应的 SQL 脚本：<pre>
 * ```java
 * List&lt;OrmDbDiffer.ResultLine> results =
 *          OrmDbDiffer.forDialect("h2")
 *                      .genDiffDdl(oldOrmModel, newOrmModel);
 * ```
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-04-26
 */
public class OrmDbDiffer {
    private final String dialectName;

    public static OrmDbDiffer forDialect(String dialectName) {
        return new OrmDbDiffer(dialectName);
    }

    private OrmDbDiffer(String dialectName) {
        this.dialectName = dialectName;
    }

    /**
     * 生成数据库 `oldDbMeta` 的 ORM 模型变更成为 `newOrmModel` 所需执行的 SQL DDL 脚本。
     * <p/>
     * `oldDbMeta` 通过 `JdbcMetaDiscovery#discover` 从数据库中根据匹配的表结构而生成：<pre>
     * ```java
     * JdbcMetaDiscovery discovery = new JdbcMetaDiscovery();
     * DataBaseMeta oldDbMeta = discovery.discover(this.dataSource, dbName, null, "DEV_%");
     * ```
     * </pre>
     * 而 `newOrmModel` 可直接通过 `DslModelHelper#loadDslModel` 加载 `app.orm.xml` 得到。
     */
    public List<ResultLine> genDiffDdl(DataBaseMeta oldDbMeta, OrmModel newOrmModel) {
        return genDiffDdl(oldDbMeta.getOrmModel(), newOrmModel);
    }

    /**
     * 对比新老 ORM 模型之间的差异并生成从 `oldOrmModel` 变更成为 `newOrmModel` 所需执行的 SQL DDL 脚本。
     * <p/>
     * 注：`oldOrmModel` 与 `newOrmModel` 均可直接通过 `DslModelHelper#loadDslModel`
     * 加载对应的 `app.orm.xml` 得到。
     */
    public List<ResultLine> genDiffDdl(OrmModel oldOrmModel, OrmModel newOrmModel) {
        IDiffValue ormModelDiff = new OrmModelDiffer().diffTables(oldOrmModel, newOrmModel);

        IDiffValue entitiesDiff = getPropDiff(ormModelDiff, "entities");
        if (entitiesDiff == null || entitiesDiff.getKeyedElementDiffs() == null) {
            return new ArrayList<>();
        }

        Map<DiffType, List<ResultLine>> resultsMap = new HashMap<>();

        DdlSqlCreator ddlSqlCreator = DdlSqlCreator.forDialect(this.dialectName);
        entitiesDiff.getKeyedElementDiffs().forEach((entityName, entityDiff) -> {
            DiffType diffType = entityDiff.getDiffType();

            List<ResultLine> results = resultsMap.computeIfAbsent(diffType, k -> new ArrayList<>());
            switch (diffType) {
                // 对表的新增
                case add: {
                    String sql = ddlSqlCreator.createTable((IEntityModel) entityDiff.getNewValue());
                    results.add(new ResultLine(diffType, sql));
                    break;
                }
                // 对表的删除
                case remove: {
                    String sql = ddlSqlCreator.dropTable((IEntityModel) entityDiff.getOldValue(), true);
                    results.add(new ResultLine(diffType, sql));
                    break;
                }
                // 对字段的增删改
                case update: {
                    IDiffValue uniqueKeysDiff = getPropDiff(entityDiff, "uniqueKeys");
                    Map<DiffType, List<ResultLine>> uniqueKeysDiffDdl = createUniqueKeysDiffDdl(ddlSqlCreator,
                                                                                                uniqueKeysDiff,
                                                                                                (IEntityModel) entityDiff.getOldValue(),
                                                                                                (IEntityModel) entityDiff.getNewValue());

                    IDiffValue columnsDiff = getPropDiff(entityDiff, "columns");
                    Map<DiffType, List<ResultLine>> columnsDiffDdl = createColumnsDiffDdl(ddlSqlCreator, columnsDiff);

                    results.addAll(columnsDiffDdl.getOrDefault(DiffType.add, new ArrayList<>()));
                    // 先删除唯一键
                    results.addAll(uniqueKeysDiffDdl.getOrDefault(DiffType.remove, new ArrayList<>()));
                    // 再删除/修改字段
                    results.addAll(columnsDiffDdl.getOrDefault(DiffType.remove, new ArrayList<>()));
                    results.addAll(columnsDiffDdl.getOrDefault(DiffType.update, new ArrayList<>()));
                    // 最后添加唯一键
                    results.addAll(uniqueKeysDiffDdl.getOrDefault(DiffType.add, new ArrayList<>()));

                    break;
                }
            }
        });

        List<ResultLine> results = new ArrayList<>();
        // 先新增表
        results.addAll(resultsMap.getOrDefault(DiffType.add, new ArrayList<>()));
        // 再修改表
        results.addAll(resultsMap.getOrDefault(DiffType.update, new ArrayList<>()));
        // 最后删除表
        results.addAll(resultsMap.getOrDefault(DiffType.remove, new ArrayList<>()));

        return results;
    }

    private Map<DiffType, List<ResultLine>> createColumnsDiffDdl(DdlSqlCreator ddlSqlCreator, IDiffValue columnsDiff) {
        if (columnsDiff == null || columnsDiff.getKeyedElementDiffs() == null) {
            return new HashMap<>();
        }

        // 需要和唯一键约束的增删做排序处理，故而，按变更类型放置 DDL
        Map<DiffType, List<ResultLine>> resultsMap = new HashMap<>();

        columnsDiff.getKeyedElementDiffs().forEach((columnName, columnDiff) -> {
            DiffType diffType = columnDiff.getDiffType();

            List<ResultLine> results = resultsMap.computeIfAbsent(diffType, k -> new ArrayList<>());
            switch (diffType) {
                // 对字段的新增
                case add: {
                    String sql = ddlSqlCreator.addColumn((IColumnModel) columnDiff.getNewValue());
                    results.add(new ResultLine(diffType, sql));
                    break;
                }
                // 对字段的删除
                case remove: {
                    String sql = ddlSqlCreator.dropColumn((IColumnModel) columnDiff.getOldValue());
                    results.add(new ResultLine(diffType, sql));
                    break;
                }
                // 对字段的修改
                case update: {
                    String sql = ddlSqlCreator.modifyColumn((IColumnModel) columnDiff.getNewValue(),
                                                            (IColumnModel) columnDiff.getOldValue());
                    results.add(new ResultLine(diffType, sql));
                    break;
                }
            }
        });

        return resultsMap;
    }

    private Map<DiffType, List<ResultLine>> createUniqueKeysDiffDdl(
            DdlSqlCreator ddlSqlCreator, IDiffValue uniqueKeysDiff, IEntityModel oldEntityModel,
            IEntityModel newEntityModel
    ) {
        if (uniqueKeysDiff == null || uniqueKeysDiff.getKeyedElementDiffs() == null) {
            return new HashMap<>();
        }

        // 需要和字段的增删改做排序处理，故而，按变更类型放置 DDL
        Map<DiffType, List<ResultLine>> resultsMap = new HashMap<>();

        uniqueKeysDiff.getKeyedElementDiffs().forEach((columnName, uniqueKeyDiff) -> {
            DiffType diffType = uniqueKeyDiff.getDiffType();

            List<ResultLine> results = resultsMap.computeIfAbsent(diffType, k -> new ArrayList<>());
            switch (diffType) {
                // 对唯一键的新增
                case add: {
                    String sql = ddlSqlCreator.addUniqueKey(newEntityModel,
                                                            (OrmUniqueKeyModel) uniqueKeyDiff.getNewValue());
                    results.add(new ResultLine(diffType, sql));
                    break;
                }
                // 对唯一键的删除
                case remove: {
                    String sql = ddlSqlCreator.dropUniqueKey(oldEntityModel,
                                                             (OrmUniqueKeyModel) uniqueKeyDiff.getOldValue());
                    results.add(new ResultLine(diffType, sql));
                    break;
                }
                // 唯一键不支持修改，只能先删后增
                case update: {
                    String sql = ddlSqlCreator.dropUniqueKey(oldEntityModel,
                                                             (OrmUniqueKeyModel) uniqueKeyDiff.getOldValue());

                    results = resultsMap.computeIfAbsent(DiffType.remove, k -> new ArrayList<>());
                    results.add(new ResultLine(DiffType.remove, sql));

                    sql = ddlSqlCreator.addUniqueKey(newEntityModel, (OrmUniqueKeyModel) uniqueKeyDiff.getNewValue());

                    results = resultsMap.computeIfAbsent(DiffType.add, k -> new ArrayList<>());
                    results.add(new ResultLine(DiffType.add, sql));

                    break;
                }
            }
        });

        return resultsMap;
    }

    private IDiffValue getPropDiff(IDiffValue diff, String prop) {
        Map<String, ? extends IDiffValue> propDiffs = diff.getPropDiffs();

        return propDiffs != null ? propDiffs.get(prop) : null;
    }

    /** 以行的变更情况作为差异结果，方便调用方根据变更类型做出进一步处理 */
    @DataBean
    public static class ResultLine {
        private final DiffType type;
        private final String line;

        public ResultLine(DiffType type, String line) {
            this.type = type;
            this.line = line;
        }

        public DiffType getType() {
            return this.type;
        }

        public String getLine() {
            return this.line;
        }
    }
}
