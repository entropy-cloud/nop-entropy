/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.text.tokenizer;

public final class UnknownPeekToken implements IToken {
    public static final UnknownPeekToken INSTANCE = new UnknownPeekToken();

    @Override
    public String getText() {
        return "<unknown>";
    }
}
