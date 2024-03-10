/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

import io.nop.xlang.ast._gen._DecoratedDeclaration;

import java.util.Objects;

public abstract class DecoratedDeclaration extends _DecoratedDeclaration implements Decorated {
    public Decorator getDecorator(String name) {
        Decorators decorators = this.getDecorators();
        if (decorators == null || decorators.isEmpty())
            return null;

        for (Decorator decorator : decorators.getDecorators()) {
            if (Objects.equals(name, decorator.getName()))
                return decorator;
        }

        return null;
    }

    public boolean hasDecorator(String name) {
        return getDecorator(name) != null;
    }
}