/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.xlsx.output;

import io.nop.core.context.IEvalContext;
import io.nop.excel.model.IExcelSheet;

import java.util.function.Consumer;

public interface IExcelSheetGenerator {
    void generate(IEvalContext context, Consumer<IExcelSheet> consumer);
}
