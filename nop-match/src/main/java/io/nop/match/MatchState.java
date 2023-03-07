/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.match;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.commons.util.ClassHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.json.utils.JsonVisitState;
import io.nop.core.lang.json.utils.SourceLocationHelper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.match.MatchConstants.VAR_MATCH_STATE;
import static io.nop.match.MatchErrors.ARG_JSON_FIELD;
import static io.nop.match.MatchErrors.ARG_JSON_PATH;
import static io.nop.match.MatchErrors.ARG_PARENT_CLASS;
import static io.nop.match.MatchErrors.ARG_VALUE;

public class MatchState extends JsonVisitState implements ISourceLocationGetter {
    private SourceLocation location;
    private Object parent;
    private Object value;
    private IEvalContext scope = DisabledEvalScope.INSTANCE;

    /**
     * 是否忽略模板中没有定义的字段
     */
    private boolean ignoreUnknown;
    private IValidationErrorCollector errorCollector = IValidationErrorCollector.THROW_ERROR;

    public MatchState(Object root) {
        super(root);
        this.value = root;
        this.location = SourceLocationHelper.getBeanLocation(root);
    }

    public ErrorBean buildError(ErrorCode errorCode) {
        return errorCollector.buildError(errorCode.getErrorCode()).description(errorCode.getDescription())
                .param(ARG_JSON_PATH, getJsonPathString()).param(ARG_JSON_FIELD, getJsonField())
                .param(ARG_VALUE, safeToString(value)).param(ARG_PARENT_CLASS, getParentClassName());
    }

    String safeToString(Object v) {
        if (value == null)
            return "null";

        if (v instanceof Map)
            return "Map[" + ((Map<?, ?>) v).keySet() + "]";
        if (v instanceof List) {
            return "List[" + ((List) v).size() + "]";
        } else if (v instanceof Set) {
            return "Set[" + ((Set) v).size() + "]";
        }
        return String.valueOf(v);
    }

    public String getParentClassName() {
        return parent == null ? null : ClassHelper.getCanonicalClassName(parent.getClass());
    }

    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public Object getParent() {
        return parent;
    }

    public void setParent(Object parent) {
        this.parent = parent;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public IEvalContext getScope() {
        return scope;
    }

    public void setScope(IEvalContext scope) {
        this.scope = scope;
        scope.getEvalScope().setLocalValue(null, VAR_MATCH_STATE, this);
    }

    public IValidationErrorCollector getErrorCollector() {
        return errorCollector;
    }

    public void setErrorCollector(IValidationErrorCollector errorCollector) {
        this.errorCollector = errorCollector;
    }

    public boolean isIgnoreUnknown() {
        return ignoreUnknown;
    }

    public void setIgnoreUnknown(boolean ignoreUnknown) {
        this.ignoreUnknown = ignoreUnknown;
    }
}