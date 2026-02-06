package io.nop.xlang.xmeta.validate;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.MapCache;
import io.nop.core.context.IEvalContext;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.ISchemaLoader;

import java.util.ArrayDeque;
import java.util.Deque;

import static io.nop.xlang.XLangConstants.VAR_VALIDATION_CTX;

public class ValidationContext implements IEvalContext {
    private final IServiceContext serviceContext;
    private final IEvalScope scope;
    private ISchemaLoader schemaLoader;
    private final Deque<IObjPropMeta> propStack = new ArrayDeque<>();
    private IValidationErrorCollector errorCollector = IValidationErrorCollector.THROW_ERROR;
    private ICache<Object, Object> cache;
    private String locale;
    private boolean disableGetter;
    private boolean disableSetter;

    public ValidationContext(IServiceContext serviceContext) {
        this.serviceContext = serviceContext;
        this.scope = serviceContext == null ? XLang.newEvalScope() : serviceContext.getEvalScope().newChildScope();
        this.scope.setLocalValue(VAR_VALIDATION_CTX, this);
    }

    public ValidationContext() {
        this(IServiceContext.getCtx());
    }

    public boolean isDisableGetter() {
        return disableGetter;
    }

    public void setDisableGetter(boolean disableGetter) {
        this.disableGetter = disableGetter;
    }

    public boolean isDisableSetter() {
        return disableSetter;
    }

    public void setDisableSetter(boolean disableSetter) {
        this.disableSetter = disableSetter;
    }

    @Override
    public IEvalScope getEvalScope() {
        return scope;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public IServiceContext getServiceContext() {
        return serviceContext;
    }

    public void enterProp(IObjPropMeta prop) {
        propStack.push(prop);
    }

    public void leave() {
        propStack.pop();
    }

    public ISchemaLoader getSchemaLoader() {
        return schemaLoader;
    }

    public void setSchemaLoader(ISchemaLoader schemaLoader) {
        this.schemaLoader = schemaLoader;
    }

    public IValidationErrorCollector getErrorCollector() {
        return errorCollector;
    }

    public void setErrorCollector(IValidationErrorCollector errorCollector) {
        this.errorCollector = errorCollector;
    }

    public ICache<Object, Object> getCache() {
        if (cache == null) {
            if (serviceContext != null)
                cache = serviceContext.getCache();
            else
                cache = new MapCache<>();
        }
        return cache;
    }

    public ErrorBean addError(ErrorCode errorCode) {
        ErrorBean errorBean = errorCollector.buildError(errorCode);
        errorCollector.addError(errorBean);
        return errorBean;
    }
}