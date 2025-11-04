/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.loop;

import java.util.Collection;
import java.util.stream.Stream;

public interface INestedLoopSupport {

    Stream<INestedLoopVar> stream();

    boolean hasVar(String varName);

    INestedLoop loopForVar(String varName);

    default Stream<INestedLoopVar> streamForVar(String varName) {
        return loopForVar(varName).stream();
    }

    default INestedLoop loopForVars(Collection<String> varNames) {
        INestedLoop loop = loopForVar(null);

        for (String varName : varNames) {
            loop = loop.loopForVar(varName);
        }
        return loop;
    }
}