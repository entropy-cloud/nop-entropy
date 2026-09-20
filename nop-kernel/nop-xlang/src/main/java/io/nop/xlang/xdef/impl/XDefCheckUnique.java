package io.nop.xlang.xdef.impl;

import io.nop.xlang.xdef.XDefCheckScope;
import io.nop.xlang.xdef.impl._gen._XDefCheckUnique;

public class XDefCheckUnique extends _XDefCheckUnique{
    public XDefCheckUnique(){

    }

    @Override
    public XDefCheckScope getCheckScope() {
        return this.getScope();
    }
}
