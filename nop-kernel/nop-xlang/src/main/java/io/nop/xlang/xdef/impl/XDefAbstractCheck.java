package io.nop.xlang.xdef.impl;

import io.nop.xlang.xdef.XDefCheckScope;
import io.nop.xlang.xdef.impl._gen._XDefAbstractCheck;

public class XDefAbstractCheck extends _XDefAbstractCheck{
    public XDefAbstractCheck(){

    }

    /**
     * 只有check-unique/check-ref声明scope属性，其余约束规则恒为null（天然文档级）
     */
    public XDefCheckScope getCheckScope() {
        return null;
    }
}
