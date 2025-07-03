package io.nop.xlang.xdef.domain;

import io.nop.xlang.xdef.IStdDomainHandler;

public class XJsonDomainHandler implements IStdDomainHandler {
    public static final XJsonDomainHandler INSTANCE = new XJsonDomainHandler();

    @Override
    public String getName() {
        return "xjson";
    }

    public XJsonDomainHandler instance() {
        return INSTANCE;
    }
}
