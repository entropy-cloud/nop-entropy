package io.nop.record_mapping;

import io.nop.commons.cache.ICache;
import io.nop.commons.cache.MapCache;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;

import static io.nop.record_mapping.RecordMappingConstants.VAR_ROOT_RECORD;
import static io.nop.record_mapping.RecordMappingConstants.VAR_SOURCE_ROOT;
import static io.nop.record_mapping.RecordMappingConstants.VAR_TARGET_ROOT;

public class RecordMappingContext implements IEvalContext {
    private final IEvalScope scope;
    private ICache<Object, Object> cache;
    private boolean forceUseMap;
    private boolean skipValidation;
    private XLangCompileTool compileTool;

    private Object sourceRoot;
    private Object sourceParent;
    private Object targetRoot;
    private Object targetParent;

    public RecordMappingContext(IEvalScope scope) {
        this.scope = scope;
    }

    public RecordMappingContext() {
        this(XLang.newEvalScope());
    }

    public ICache<Object, Object> getCache() {
        if (cache == null) {
            cache = new MapCache<>("record-mapping-cache", true);
        }
        return cache;
    }

    public XLangCompileTool getCompileTool() {
        return compileTool;
    }

    public XLangCompileTool makeCompileTool() {
        if (compileTool == null)
            compileTool = XLang.newCompileTool().allowUnregisteredScopeVar(true);
        return compileTool;
    }

    public void setCompileTool(XLangCompileTool compileTool) {
        this.compileTool = compileTool;
    }

    public Object getValue(String varName) {
        return scope.getValue(varName);
    }

    public void setValue(String varName, Object varValue) {
        scope.setLocalValue(varName, varValue);
    }

    public boolean isForceUseMap() {
        return forceUseMap;
    }

    public void setForceUseMap(boolean forceUseMap) {
        this.forceUseMap = forceUseMap;
    }

    public boolean isSkipValidation() {
        return skipValidation;
    }

    public void setSkipValidation(boolean skipValidation) {
        this.skipValidation = skipValidation;
    }

    @Override
    public IEvalScope getEvalScope() {
        return scope;
    }

    public Object getSourceRoot() {
        return sourceRoot;
    }

    public void setSourceRoot(Object sourceRoot) {
        this.sourceRoot = sourceRoot;
        this.scope.setLocalValue(VAR_SOURCE_ROOT, sourceRoot);
    }

    public Object getSourceParent() {
        return sourceParent;
    }

    public void setSourceParent(Object sourceParent) {
        this.sourceParent = sourceParent;
    }

    public Object getTargetRoot() {
        return targetRoot;
    }

    public void setTargetRoot(Object targetRoot) {
        this.targetRoot = targetRoot;
        this.scope.setLocalValue(VAR_ROOT_RECORD, targetRoot);
        this.scope.setLocalValue(VAR_TARGET_ROOT, targetRoot);
    }

    public Object getTargetParent() {
        return targetParent;
    }

    public void setTargetParent(Object targetParent) {
        this.targetParent = targetParent;
    }
}
