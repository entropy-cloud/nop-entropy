/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.bind;

import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalExprParser;
import io.nop.core.lang.json.utils.JsonVisitState;

public class ValueResolverCompileOptions extends JsonVisitState {
    private IEvalExprParser exprParser = EvalExprProvider.getDefaultExprParser();
    private ValueResolverCompilerRegistry registry = ValueResolverCompilerRegistry.DEFAULT;

    /**
     * 如果发现未识别的前缀表达式，是否抛出异常
     */
    private boolean ignoreUnknown;

    /**
     * 忽略所有null值
     */
    private boolean ignoreNull;

    public ValueResolverCompileOptions(Object root) {
        super(root);
    }

    public boolean isIgnoreUnknown() {
        return ignoreUnknown;
    }

    public void setIgnoreUnknown(boolean ignoreUnknown) {
        this.ignoreUnknown = ignoreUnknown;
    }

    public boolean isIgnoreNull() {
        return ignoreNull;
    }

    public void setIgnoreNull(boolean ignoreNull) {
        this.ignoreNull = ignoreNull;
    }

    public ValueResolverCompilerRegistry getRegistry() {
        return registry;
    }

    public void setRegistry(ValueResolverCompilerRegistry registry) {
        this.registry = registry;
    }

    public IEvalExprParser getExprParser() {
        return exprParser;
    }

    public void setExprParser(IEvalExprParser exprParser) {
        this.exprParser = exprParser;
    }
}
