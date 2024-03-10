/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.model;

import io.nop.core.model.table.CellPosition;
import io.nop.excel.model._gen._ExcelClientAnchor;

public class ExcelClientAnchor extends _ExcelClientAnchor {
    public ExcelClientAnchor() {

    }

    public ExcelClientAnchor copy(){
        ExcelClientAnchor ret = new ExcelClientAnchor();
        ret.setRow1(getRow1());
        ret.setCol1(getCol1());

        ret.setRowDelta(getRowDelta());
        ret.setColDelta(getColDelta());
        ret.setDx1(getDx1());
        ret.setDx2(getDx2());
        ret.setDy1(getDy1());
        ret.setDy2(getDy2());
        ret.setType(getType());
        return ret;
    }

    public CellPosition getStartPosition() {
        return CellPosition.of(getRow1(), getCol1());
    }
}
