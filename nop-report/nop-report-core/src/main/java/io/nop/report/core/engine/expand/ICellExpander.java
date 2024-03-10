/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine.expand;

import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.engine.IXptRuntime;

import java.util.Deque;

public interface ICellExpander {
    void expand(ExpandedCell cell, Deque<ExpandedCell> processing, IXptRuntime xptRt);
}