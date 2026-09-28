/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.jpath;

/**
 * Evaluation SPI for {@link JPath}. The nop-jq module registers an implementation
 * (delegating to {@code io.nop.jpath.NopJsonPath}) during platform
 * initialization via {@code JqJPathInitializer}.
 *
 * <p>Implementations must never be null-hostile: {@link JPath} fails fast with
 * {@code nop.err.core.jpath.no-evaluator} when no evaluator is registered.
 */
public interface JPathEvaluator {

    /**
     * Evaluate the path against the bean and return all matches.
     */
    Object eval(String path, Object bean);

    /**
     * Evaluate the path against the bean and return the first match, or null.
     */
    Object evalOne(String path, Object bean);

    /**
     * Set the value selected by the path. Returns false when the target
     * container does not exist (no intermediate containers are created).
     */
    boolean set(String path, Object bean, Object value);

    /**
     * Remove the value selected by the path. Returns false when nothing matched.
     */
    boolean remove(String path, Object bean);
}
