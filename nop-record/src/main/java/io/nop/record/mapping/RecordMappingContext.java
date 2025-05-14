package io.nop.record.mapping;

import io.nop.commons.cache.ICache;
import io.nop.commons.cache.MapCache;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;

public class RecordMappingContext implements IEvalContext {
    private final IEvalScope scope;
    private ICache<Object, Object> cache;

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

    @Override
    public IEvalScope getEvalScope() {
        return scope;
    }

    public Object getSourceRoot() {
        return sourceRoot;
    }

    public void setSourceRoot(Object sourceRoot) {
        this.sourceRoot = sourceRoot;
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
    }

    public Object getTargetParent() {
        return targetParent;
    }

    public void setTargetParent(Object targetParent) {
        this.targetParent = targetParent;
    }
}
