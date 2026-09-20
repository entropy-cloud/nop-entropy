package io.nop.xlang.xdef.impl;

import io.nop.xlang.xdef.XDefCheckScope;
import io.nop.xlang.xdef.impl._gen._XDefCheckRef;

public class XDefCheckRef extends _XDefCheckRef{
    public XDefCheckRef(){

    }

    @Override
    public XDefCheckScope getCheckScope() {
        return this.getScope();
    }
}
