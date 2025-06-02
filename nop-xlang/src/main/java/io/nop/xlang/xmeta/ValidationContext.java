package io.nop.xlang.xmeta;

import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.core.dict.IDictLoader;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;

public class ValidationContext {
    private final IEvalScope scope;
    private final IValidationErrorCollector errorCollector;
    private IDictLoader dictLoader;

    public ValidationContext(IEvalScope scope, IValidationErrorCollector errorCollector) {
        this.scope = scope;
        this.errorCollector = errorCollector;
    }

    public ValidationContext(IEvalScope scope) {
        this(scope, IValidationErrorCollector.THROW_ERROR);
    }

    public ValidationContext() {
        this(XLang.newEvalScope());
    }

    public IEvalScope getScope() {
        return scope;
    }

    public IValidationErrorCollector getErrorCollector() {
        return errorCollector;
    }

    public IDictLoader getDictLoader() {
        return dictLoader;
    }

    public void setDictLoader(IDictLoader dictLoader) {
        this.dictLoader = dictLoader;
    }
}
