/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.ISourceLocationSetter;

public interface IConditionalExpression extends ISourceLocationGetter, ISourceLocationSetter {
    Expression getTest();

    void setTest(Expression test);

    Expression getConsequent();

    void setConsequent(Expression consequent);

    Expression getAlternate();

    void setAlternate(Expression alternate);

    IConditionalExpression createInstance();
}
