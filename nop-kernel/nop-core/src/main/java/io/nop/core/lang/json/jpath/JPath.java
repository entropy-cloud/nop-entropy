/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.jpath;

import io.nop.api.core.exceptions.NopException;

import static io.nop.core.CoreErrors.ARG_PATH;
import static io.nop.core.CoreErrors.ERR_JPATH_NO_EVALUATOR;

/**
 * JSON path query entry retained for backward compatibility. Compilation
 * ({@link #compile(String)}) always works; evaluation is delegated to the
 * {@link JPathEvaluator} registered by the nop-jq module (bound to
 * {@code io.nop.jq.jsonpath.NopJsonPath}) on platform initialization.
 *
 * <p>Calling any evaluation method when no evaluator is registered fails fast
 * with {@code nop.err.core.jpath.no-evaluator}: add a nop-jq dependency to the
 * classpath to enable evaluation. Code that already depends on nop-jq should
 * prefer {@code io.nop.jq.jsonpath.NopJsonPath} directly.
 *
 * <p>Note: the instance method {@code get(bean, value)} performs a set
 * operation (historical naming kept for compatibility).
 */
public class JPath {

    private static volatile JPathEvaluator evaluator;

    private final String pathString;

    public JPath(String pathString) {
        this.pathString = pathString;
    }

    public static JPath compile(String path) {
        return new JPath(path);
    }

    public static JPath jpath(String path) {
        return compile(path);
    }

    public static JPath compileWithCache(String path) {
        return compile(path);
    }

    public String getPathString() {
        return pathString;
    }

    /**
     * Register the evaluation bridge. Called by nop-jq's core initializer;
     * the last registration wins and the previous one is replaced.
     */
    public static void registerEvaluator(JPathEvaluator impl) {
        evaluator = impl;
    }

    /**
     * Remove the registration if it is still the given instance.
     */
    public static void unregisterEvaluator(JPathEvaluator impl) {
        if (evaluator == impl)
            evaluator = null;
    }

    public Object get(Object bean) {
        return requireEvaluator(pathString).eval(pathString, bean);
    }

    public Object getOne(Object bean) {
        return requireEvaluator(pathString).evalOne(pathString, bean);
    }

    public void get(Object bean, Object value) {
        requireEvaluator(pathString).set(pathString, bean, value);
    }

    public void delete(Object bean) {
        requireEvaluator(pathString).remove(pathString, bean);
    }

    // Note: the historical static convenience forms get(bean, path[, value]) and
    // delete(bean, path) were removed. They had zero callers in the repository and
    // collided with the instance overloads: through an instance reference the more
    // specific static get(Object, String) always won, silently treating a set-value
    // argument as a path.

    private static JPathEvaluator requireEvaluator(String path) {
        JPathEvaluator impl = evaluator;
        if (impl == null)
            throw new NopException(ERR_JPATH_NO_EVALUATOR).param(ARG_PATH, path);
        return impl;
    }
}
