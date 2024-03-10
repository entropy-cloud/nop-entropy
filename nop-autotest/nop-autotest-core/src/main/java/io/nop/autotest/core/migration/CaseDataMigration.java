/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.autotest.core.migration;

import io.nop.autotest.core.data.AutoTestCaseData;
import io.nop.autotest.core.migration.operations.DeleteTableMigration;
import io.nop.autotest.core.migration.operations.RenameTableMigration;
import io.nop.autotest.core.migration.operations.TransformTableMigration;
import io.nop.commons.util.FileHelper;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.util.ArrayList;
import java.util.List;

public class CaseDataMigration {
    private List<MigrationOperation> operations = new ArrayList<>();

    public static CaseDataMigration createMigration() {
        return new CaseDataMigration();
    }

    public CaseDataMigration deleteTable(String tableName) {
        operations.add(new DeleteTableMigration(tableName));
        return this;
    }

    public CaseDataMigration renameTable(String oldTableName, String newTableName) {
        operations.add(new RenameTableMigration(oldTableName, newTableName));
        return this;
    }

    public TableMigrationConfig forTable(String tableName) {
        TransformTableMigration op = new TransformTableMigration(tableName);
        operations.add(op);
        return op;
    }

    public void run(AutoTestCaseData caseData) {
        for (MigrationOperation op : operations) {
            op.run(caseData);
        }
    }

    public void runForAllCases(File casesDir) {
        FileHelper.walk(casesDir, file -> {
            File autotest = new File(file, "autotest.yaml");
            if (autotest.exists()) {
                run(new AutoTestCaseData(file, null));
                return FileVisitResult.SKIP_SUBTREE;
            } else {
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
