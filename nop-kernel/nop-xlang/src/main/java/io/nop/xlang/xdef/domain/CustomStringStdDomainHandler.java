package io.nop.xlang.xdef.domain;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.xlang.api.XLangCompileTool;

public class CustomStringStdDomainHandler extends StringStdDomainHandler {
    private final String name;

    public CustomStringStdDomainHandler(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object parseProp(String options, SourceLocation loc, String propName, Object text, XLangCompileTool cp) {
        return StringHelper.toString(text, null);
    }
}
