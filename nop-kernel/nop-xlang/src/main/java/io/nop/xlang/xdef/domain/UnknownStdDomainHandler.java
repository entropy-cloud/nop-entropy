package io.nop.xlang.xdef.domain;

import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdef.IStdDomainHandler;

public class UnknownStdDomainHandler implements IStdDomainHandler {
    private final String name;

    public UnknownStdDomainHandler(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public IGenericType getGenericType(boolean mandatory, String options) {
        return PredefinedGenericTypes.ANY_TYPE;
    }

    @Override
    public Object parseProp(String options, SourceLocation loc, String propName, Object text, XLangCompileTool cp) {
        return text;
    }

    @Override
    public void validate(SourceLocation loc, String propName, Object value, IValidationErrorCollector collector) {

    }
}
