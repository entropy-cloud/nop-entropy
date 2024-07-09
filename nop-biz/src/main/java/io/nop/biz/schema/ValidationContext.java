package io.nop.biz.schema;

import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;

import java.util.List;

public class ValidationContext implements IEvalContext {
    private final IEvalScope scope;
    private SourceLocation location;
    private List<String> propPaths;
    private final IValidationErrorCollector errorCollector;

    public ValidationContext(IEvalScope scope, IValidationErrorCollector errorCollector) {
        this.scope = scope == null ? XLang.newEvalScope() : scope;
        this.errorCollector = errorCollector;
    }

    public ValidationContext(IEvalScope scope) {
        this(scope, IValidationErrorCollector.THROW_ERROR);
    }

    @Override
    public IEvalScope getEvalScope() {
        return scope;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public IValidationErrorCollector getErrorCollector() {
        return errorCollector;
    }

    public List<String> getPropPaths() {
        return propPaths;
    }

    public void setPropPaths(List<String> propPaths) {
        this.propPaths = propPaths;
    }
}
