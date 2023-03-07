/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdef.source;

import io.nop.api.core.json.IJsonString;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.xml.IXNodeGenerator;
import io.nop.core.lang.xml.XNode;

public class SourceXNodeGenerator implements IXNodeGenerator, IJsonString, IWithSourceCode {
    private final String source;
    private final IXNodeGenerator action;

    public SourceXNodeGenerator(String source, IXNodeGenerator action) {
        this.source = source;
        this.action = action;
    }

    public String getSource() {
        return source;
    }

    public String toString() {
        return source;
    }

    @Override
    public XNode generateNode(IEvalContext context) {
        return action.generateNode(context);
    }
}