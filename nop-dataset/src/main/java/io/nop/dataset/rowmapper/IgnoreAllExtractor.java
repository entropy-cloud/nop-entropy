/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.rowmapper;

import io.nop.dataset.IDataSet;

import java.util.function.Function;

public class IgnoreAllExtractor implements Function<IDataSet, Void> {
    public static final IgnoreAllExtractor INSTANCE = new IgnoreAllExtractor();

    @Override
    public Void apply(IDataSet ds) {
        while (ds.hasNext()) {
            ds.next();
        }
        return null;
    }
}
