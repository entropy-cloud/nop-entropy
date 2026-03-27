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
import io.nop.db.migration.model.DbMigrationModel;
import io.nop.xlang.xdsl.DslModelParser;

import java.util.ArrayList;
import java.util.List;

public class MigrationFileScanner {
    
    private static final String SCHEMA_PATH = "/nop/schema/db-migration/migration.xdef";
    private static final String MIGRATION_EXTENSION = ".migration.xml";
    
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
        return (DbMigrationModel) new DslModelParser(SCHEMA_PATH)
            .parseFromResource(resource);
    }
}
