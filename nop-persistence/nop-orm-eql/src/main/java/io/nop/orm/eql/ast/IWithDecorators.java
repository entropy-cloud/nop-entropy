/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import java.util.List;

public interface IWithDecorators {
    List<SqlDecorator> getDecorators();

    default SqlDecorator getDecorator(String name) {
        List<SqlDecorator> decorators = getDecorators();
        if (decorators == null || decorators.isEmpty())
            return null;
        for (SqlDecorator decorator : decorators) {
            if (name.equals(decorator.getName()))
                return decorator;
        }
        return null;
    }

    default boolean hasDecorator(String name) {
        return getDecorator(name) != null;
    }
}
