/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.autotest.core.migration.operations;

import io.nop.autotest.core.data.AutoTestCaseData;
import io.nop.autotest.core.migration.MigrationOperation;

public class DeleteTableMigration extends MigrationOperation {
    private final String tableName;

    public DeleteTableMigration(String tableName) {
        this.tableName = tableName;
    }

    @Override
    public void run(AutoTestCaseData caseData) {
        forTable(caseData, tableName, file -> file.delete());
    }
}
