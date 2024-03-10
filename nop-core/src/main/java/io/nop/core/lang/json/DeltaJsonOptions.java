/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalExprParser;
import io.nop.core.lang.eval.IPredicateEvaluator;
import io.nop.core.lang.json.bind.ValueResolverCompilerRegistry;
import io.nop.core.lang.json.delta.IDeltaExtendsGenerator;
import io.nop.core.resource.IResource;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class DeltaJsonOptions {
    private ValueResolverCompilerRegistry registry;
    private boolean ignoreUnknownValueResolver;
    private IEvalExprParser exprParser = EvalExprProvider.getDefaultExprParser();
    private IEvalContext evalContext = DisabledEvalScope.INSTANCE;
    private IDeltaExtendsGenerator extendsGenerator;
    private IPredicateEvaluator featureSwitchEvaluator;
    private Function<IResource, Map<String, Object>> resourceLoader;
    private boolean normalizeI18nKey;
    private Consumer<Object> cleaner;
    private boolean cleanDelta = true;

    public Function<IResource, Map<String, Object>> getResourceLoader() {
        return resourceLoader;
    }

    public void setResourceLoader(Function<IResource, Map<String, Object>> resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public boolean isCleanDelta() {
        return cleanDelta;
    }

    public void setCleanDelta(boolean cleanDelta) {
        this.cleanDelta = cleanDelta;
    }

    public Consumer<Object> getCleaner() {
        return cleaner;
    }

    public void setCleaner(Consumer<Object> cleaner) {
        this.cleaner = cleaner;
    }

    public IEvalExprParser getExprParser() {
        return exprParser;
    }

    public void setExprParser(IEvalExprParser exprParser) {
        this.exprParser = exprParser;
    }

    public IPredicateEvaluator getFeatureSwitchEvaluator() {
        return featureSwitchEvaluator;
    }

    public void setFeatureSwitchEvaluator(IPredicateEvaluator featureSwitchEvaluator) {
        this.featureSwitchEvaluator = featureSwitchEvaluator;
    }

    public IDeltaExtendsGenerator getExtendsGenerator() {
        return extendsGenerator;
    }

    public void setExtendsGenerator(IDeltaExtendsGenerator extendsGenerator) {
        this.extendsGenerator = extendsGenerator;
    }

    public ValueResolverCompilerRegistry getRegistry() {
        return registry;
    }

    public void setRegistry(ValueResolverCompilerRegistry registry) {
        this.registry = registry;
    }

    public boolean isIgnoreUnknownValueResolver() {
        return ignoreUnknownValueResolver;
    }

    public void setIgnoreUnknownValueResolver(boolean ignoreUnknownValueResolver) {
        this.ignoreUnknownValueResolver = ignoreUnknownValueResolver;
    }

    public IEvalContext getEvalContext() {
        return evalContext;
    }

    public void setEvalContext(IEvalContext evalContext) {
        this.evalContext = evalContext;
    }

    public boolean isNormalizeI18nKey() {
        return normalizeI18nKey;
    }

    public void setNormalizeI18nKey(boolean normalizeI18nKey) {
        this.normalizeI18nKey = normalizeI18nKey;
    }
}
