/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.loop.model;

import io.nop.commons.util.CollectionHelper;

import java.io.Serializable;
import java.util.function.Function;
import java.util.stream.Stream;

public class NestedLoopVarModel implements Serializable {
    private static final long serialVersionUID = -9074560535012260740L;

    private final String srcName;

    private final Function<?, ?> generator;

    private final String varName;

    public NestedLoopVarModel(String varName, String srcName, Function<?, ?> generator) {
        this.srcName = srcName;
        this.generator = generator;
        this.varName = varName;
    }

    public String getSrcName() {
        return srcName;
    }

    public Function<?, ?> getGenerator() {
        return generator;
    }

    public Stream<Object> generateStream(Object input) {
        return CollectionHelper.toStream(((Function) generator).apply(input), true, false);
    }

    public String getVarName() {
        return varName;
    }
}