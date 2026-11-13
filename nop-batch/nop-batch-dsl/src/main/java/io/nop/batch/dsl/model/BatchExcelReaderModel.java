package io.nop.batch.dsl.model;

import io.nop.batch.dsl.model._gen._BatchExcelReaderModel;
import io.nop.core.lang.eval.IEvalAction;

public class BatchExcelReaderModel extends _BatchExcelReaderModel implements IBatchExcelIOModel {

    /**
     * C5-001（plan 2516 裁定④）：excel-reader 的 maxCountExpr 能力恢复（1.7-C5-006 能力缺位主体闭合）。
     * 生成基类无此字段（xdef excel-reader 元素原未声明该属性），故在保留子类补字段并同步在
     * batch.xdef 的 excel-reader 元素声明 maxCountExpr 属性。语义与 file-reader 一致：截断读取，
     * 超出 maxCount 的条目不再进入处理（行为变更随 owner doc 声明）。
     */
    private IEvalAction maxCountExpr;

    public BatchExcelReaderModel() {

    }

    public IEvalAction getMaxCountExpr() {
        return maxCountExpr;
    }

    public void setMaxCountExpr(IEvalAction maxCountExpr) {
        this.maxCountExpr = maxCountExpr;
    }
}
