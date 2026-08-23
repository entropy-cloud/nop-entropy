/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.db.migration.executor.AddColumnExecutor;
import io.nop.db.migration.executor.CreateTableExecutor;
import io.nop.db.migration.executor.DeleteDataExecutor;
import io.nop.db.migration.executor.DropColumnExecutor;
import io.nop.db.migration.executor.DropIndexExecutor;
import io.nop.db.migration.executor.DropTableExecutor;
import io.nop.db.migration.executor.InsertDataExecutor;
import io.nop.db.migration.executor.UpdateDataExecutor;
import io.nop.db.migration.model.AddColumnChange;
import io.nop.db.migration.model.CreateTableChange;
import io.nop.db.migration.model.DbChangeModel;
import io.nop.db.migration.model.DbMigrationModel;
import io.nop.db.migration.model.DeleteDataChange;
import io.nop.db.migration.model.DropColumnChange;
import io.nop.db.migration.model.DropIndexChange;
import io.nop.db.migration.model.DropTableChange;
import io.nop.db.migration.model.InsertDataChange;
import io.nop.db.migration.model.UpdateDataChange;
import io.nop.db.migration.model.RollbackDefinition;
import io.nop.xlang.xdsl.DslModelParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MigrationFileScanner {

    private static final String SCHEMA_PATH = "/nop/schema/db-migration/migration.xdef";
    private static final String MIGRATION_EXTENSION = ".migration.xml";

    /**
     * 变更模型类 -> change type 的显式映射表。
     *
     * <p>背景：migration.xdef 的 changeset 以 xdef:bean-sub-type-prop="type" 声明多态，
     * 但强类型解析器 DslBeanModelParser 不使用 bean-sub-type-prop，XML 元素 tag 名
     * 不会写入 DbChangeModel.type 字段。解析后 type 恒为 null，MigrationEngine.executeChange
     * 会静默跳过所有变更并记 success=true。因此在 loadMigration 后按模型类回填 type。
     *
     * <p>tag 名与执行器 CHANGE_TYPE 常量不一致的三个数据变更必须走本映射表：
     * &lt;insert&gt; -> insertData、&lt;update&gt; -> updateData、&lt;delete&gt; -> deleteData
     * （XML tag 无 "Data" 后缀，执行器常量有）。其余 tag 与常量同名。
     *
     * <p>仅列出继承 DbChangeModel 的变更类：_DbMigrationModel.setChangeset 以
     * DbChangeModel::getId 构造 KeyedList，其余变更类（createIndex/sql/alterColumn 等）
     * 在解析期即 ClassCastException，不会进入本方法。
     */
    private static final Map<Class<?>, String> CHANGE_TYPE_BY_CLASS;

    static {
        Map<Class<?>, String> map = new HashMap<>();
        map.put(CreateTableChange.class, CreateTableExecutor.CHANGE_TYPE);
        map.put(DropTableChange.class, DropTableExecutor.CHANGE_TYPE);
        map.put(AddColumnChange.class, AddColumnExecutor.CHANGE_TYPE);
        map.put(DropColumnChange.class, DropColumnExecutor.CHANGE_TYPE);
        map.put(DropIndexChange.class, DropIndexExecutor.CHANGE_TYPE);
        map.put(InsertDataChange.class, InsertDataExecutor.CHANGE_TYPE);
        map.put(UpdateDataChange.class, UpdateDataExecutor.CHANGE_TYPE);
        map.put(DeleteDataChange.class, DeleteDataExecutor.CHANGE_TYPE);
        CHANGE_TYPE_BY_CLASS = Collections.unmodifiableMap(map);
    }

    public List<DbMigrationModel> scan(List<String> paths) {
        List<DbMigrationModel> migrations = new ArrayList<>();

        if (paths == null || paths.isEmpty()) {
            return migrations;
        }

        for (String basePath : paths) {
            List<DbMigrationModel> found = scanDirectory(basePath);
            migrations.addAll(found);
        }

        return migrations;
    }

    protected List<DbMigrationModel> scanDirectory(String basePath) {
        List<DbMigrationModel> migrations = new ArrayList<>();

        List<? extends IResource> resources = VirtualFileSystem.instance().getChildren(basePath);

        if (resources == null || resources.isEmpty()) {
            return migrations;
        }

        for (IResource resource : resources) {
            if (resource.getName().endsWith(MIGRATION_EXTENSION)) {
                DbMigrationModel model = loadMigration(resource);
                if (model != null) {
                    migrations.add(model);
                }
            }
        }

        return migrations;
    }

    protected DbMigrationModel loadMigration(IResource resource) {
        DbMigrationModel model = (DbMigrationModel) new DslModelParser(SCHEMA_PATH)
            .parseFromResource(resource);
        backfillChangeTypes(model);
        return model;
    }

    protected void backfillChangeTypes(DbMigrationModel model) {
        if (model == null) {
            return;
        }
        if (model.getChangeset() != null) {
            for (DbChangeModel change : model.getChangeset()) {
                backfillChangeType(change);
            }
        }
        RollbackDefinition rollback = model.getRollback();
        if (rollback != null && rollback.getChanges() != null) {
            for (DbChangeModel change : rollback.getChanges()) {
                backfillChangeType(change);
            }
        }
    }

    private void backfillChangeType(DbChangeModel change) {
        if (change == null || change.getType() != null) {
            return;
        }
        String changeType = CHANGE_TYPE_BY_CLASS.get(change.getClass());
        if (changeType != null) {
            change.setType(changeType);
        }
    }
}
