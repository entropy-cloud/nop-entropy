/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.dataset;

import java.util.List;

public class KeyedReportDataSet extends ReportDataSet {
    private final Object key;

    public KeyedReportDataSet(String dsName, List<Object> items, Object key) {
        super(dsName, items);
        this.key = key;
    }

    public String toString() {
        return "KeyedReportDataSet[" + key + "]";
    }

    public Object getKey() {
        return key;
    }

}