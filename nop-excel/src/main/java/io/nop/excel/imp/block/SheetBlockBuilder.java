/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.imp.block;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.excel.model.ExcelSheet;

public class SheetBlockBuilder {
    private final ExcelSheet template;
    private final FieldsBlock blockModel;

    public SheetBlockBuilder(ExcelSheet template, FieldsBlock blockModel) {
        this.template = template;
        this.blockModel = blockModel;
    }

    public ExcelSheet getTemplate() {
        return template;
    }

    public FieldsBlock getBlockModel() {
        return blockModel;
    }

    public ExcelSheet build(Object bean, IEvalScope scope) {
        return null;
    }
}
