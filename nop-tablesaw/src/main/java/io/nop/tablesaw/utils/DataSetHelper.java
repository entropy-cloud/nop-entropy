/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tablesaw.utils;

import io.nop.dataset.IDataSet;
import io.nop.tablesaw.dataset.DataSetToTableTransformer;
import tech.tablesaw.api.Table;

import java.util.function.Function;

public class DataSetHelper {
    public static Table dataSetToTable(String name, IDataSet ds) {
        return new DataSetToTableTransformer(name).apply(ds);
    }

    public static Function<IDataSet, Table> toTableTransformer(String name) {
        return new DataSetToTableTransformer(name);
    }
}