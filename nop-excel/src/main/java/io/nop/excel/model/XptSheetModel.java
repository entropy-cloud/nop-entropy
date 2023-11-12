/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.model;

import io.nop.excel.model._gen._XptSheetModel;

public class XptSheetModel extends _XptSheetModel implements ILoopModel {
    /**
     * 是否有单元格中设置了导出Excel公式。这个标记主要用于性能优化，避免无谓的处理
     */
    private boolean useExportFormula;

    public XptSheetModel() {

    }

    public boolean isUseExportFormula() {
        return useExportFormula;
    }

    public void setUseExportFormula(boolean useExportFormula) {
        this.useExportFormula = useExportFormula;
    }
}
