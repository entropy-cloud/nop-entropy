/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.autotest.core.migration;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public interface TableMigrationConfig {
    TableMigrationConfig renameCol(String oldCol, String newCol);

    TableMigrationConfig deleteCol(String col);

    TableMigrationConfig transformCol(String col, Function<Object, Object> transformer);

    TableMigrationConfig transformRow(BiConsumer<File, Map<String, Object>> transformer);

    TableMigrationConfig transformTable(BiConsumer<File, List<Map<String, Object>>> transformer);
}
