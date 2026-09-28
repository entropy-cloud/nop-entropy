/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.jpath;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.core.lang.json.jpath.JPath;
import io.nop.core.lang.json.jpath.JPathEvaluator;
import io.nop.jpath.NopJsonPath;

/**
 * Registers the JPath evaluation bridge on platform initialization so that
 * legacy {@code io.nop.core.lang.json.jpath.JPath} consumers delegate to
 * {@link NopJsonPath}. Discovered via ServiceLoader from
 * {@code META-INF/services/io.nop.core.initialize.ICoreInitializer}.
 */
public class JqJPathInitializer implements ICoreInitializer {

    private final JPathEvaluatorImpl evaluator = new JPathEvaluatorImpl();

    @Override
    public int order() {
        // after component registration, before IOC starts consuming JPath
        return CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT + 1;
    }

    @Override
    public void initialize() {
        JPath.registerEvaluator(evaluator);
    }

    @Override
    public void destroy() {
        JPath.unregisterEvaluator(evaluator);
    }

    static final class JPathEvaluatorImpl implements JPathEvaluator {

        @Override
        public Object eval(String path, Object bean) {
            return NopJsonPath.eval(bean, path);
        }

        @Override
        public Object evalOne(String path, Object bean) {
            return NopJsonPath.evalOne(bean, path);
        }

        @Override
        public boolean set(String path, Object bean, Object value) {
            return NopJsonPath.set(bean, path, value);
        }

        @Override
        public boolean remove(String path, Object bean) {
            return NopJsonPath.remove(bean, path);
        }
    }
}
