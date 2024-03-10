/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.autotest.core.migration;

import io.nop.autotest.core.data.AutoTestCaseData;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public abstract class MigrationOperation {
    public abstract void run(AutoTestCaseData caseData);

    public void forTable(AutoTestCaseData caseData, String tableName, Consumer<File> action) {
        List<String> variants = caseData.getVariants(true);
        for (String variant : variants) {
            File inputFile = caseData.getInputTableFile(tableName, variant);
            if (inputFile.exists()) {
                action.accept(inputFile);
            }

            File outputFile = caseData.getOutputTableFile(tableName, variant);
            if (outputFile.exists()) {
                action.accept(outputFile);
            }
        }
    }
}
