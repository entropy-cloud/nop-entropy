/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.eval;

import io.nop.api.core.util.SourceLocation;

public class DisabledEvalOutput implements IEvalOutput {
    public static final DisabledEvalOutput INSTANCE = new DisabledEvalOutput();

    @Override
    public void comment(String comment) {

    }

    @Override
    public void value(SourceLocation loc, Object value) {

    }

    @Override
    public void text(SourceLocation loc, String text) {

    }
}
