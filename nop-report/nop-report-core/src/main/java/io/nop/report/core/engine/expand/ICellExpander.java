/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.engine.expand;

import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.engine.IXptRuntime;

import java.util.Deque;

public interface ICellExpander {
    void expand(ExpandedCell cell, Deque<ExpandedCell> processing, IXptRuntime xptRt);
}